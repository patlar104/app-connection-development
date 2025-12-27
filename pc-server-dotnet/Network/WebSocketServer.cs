using System.Net;
using System.Net.Security;
using System.Net.WebSockets;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Threading;
using Microsoft.Extensions.Logging;
using AppConnectServer.Core;
using AppConnectServer.Clipboard;

namespace AppConnectServer.Network;

public class WebSocketServer
{
    private readonly ILogger<WebSocketServer> _logger;
    private readonly Configuration _config;
    private readonly X509Certificate2? _certificate;
    private readonly KeyExchangeManager _keyExchange;
    private readonly Dictionary<WebSocket, ConnectionState> _connections = new();
    private HttpListener? _listener;
    private CancellationTokenSource? _cancellationTokenSource;

    public WebSocketServer(
        X509Certificate2? certificate,
        Configuration config,
        ILoggerFactory loggerFactory,
        Action<string>? onClipboardReceived = null)
    {
        _certificate = certificate;
        _config = config;
        _logger = loggerFactory.CreateLogger<WebSocketServer>();
        OnClipboardReceived = onClipboardReceived;
        _keyExchange = new KeyExchangeManager(_config.RsaKeyFile, loggerFactory);
    }

    public Action<string>? OnClipboardReceived { get; set; }
    
    public void Stop()
    {
        _cancellationTokenSource?.Cancel();
        try
        {
            _listener?.Stop();
            _listener?.Close();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Error stopping HttpListener");
        }
    }

    public async Task StartAsync(string host, int port)
    {
        _logger.LogInformation("Starting WebSocket server on {Host}:{Port}", host, port);

        _cancellationTokenSource = new CancellationTokenSource();
        _listener = new HttpListener();
        
        // Note: HttpListener with HTTPS requires certificate binding
        // For now, we'll use HTTP and add HTTPS support later
        // On macOS/Linux, HttpListener may require elevated permissions for ports <= 1024
        // For ports > 1024, it should work without sudo
        
        // Validate port
        if (port <= 0 || port > 65535)
        {
            throw new ArgumentException($"Invalid port number: {port}. Port must be between 1 and 65535.");
        }
        
        // Warn if using privileged port on Unix-like systems
        if (port <= 1024 && (Environment.OSVersion.Platform == PlatformID.Unix || 
                            Environment.OSVersion.Platform == PlatformID.MacOSX))
        {
            _logger.LogWarning(
                "Port {Port} is a privileged port. On macOS/Linux, HttpListener may require elevated permissions. " +
                "Consider using a port > 1024 to avoid permission issues.", port);
        }
        
        var prefix = $"http://{host}:{port}/";
        
        try
        {
            _listener.Prefixes.Add(prefix);
            
            // Try to start HttpListener with better error handling
            try
            {
                _listener.Start();
                _logger.LogInformation("WebSocket server running on {Prefix}", prefix);
            }
            catch (HttpListenerException ex)
            {
                _logger.LogError(ex, 
                    "Failed to start HttpListener. This may require elevated permissions on macOS/Linux. " +
                    "Error code: {ErrorCode}, Message: {Message}", ex.ErrorCode, ex.Message);
                throw new InvalidOperationException(
                    $"Cannot start HTTP server on {prefix}. " +
                    $"On macOS/Linux, you may need to run with elevated permissions or use a port > 1024. " +
                    $"Original error: {ex.Message}", ex);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Unexpected error starting HttpListener");
                throw;
            }

            // Accept connections in a loop
            // Note: GetContextAsync doesn't support cancellation tokens directly,
            // so we check cancellation between accepts
            while (!_cancellationTokenSource.Token.IsCancellationRequested)
            {
                try
                {
                    // Accept connection (this blocks until a connection arrives)
                    var context = await _listener.GetContextAsync();
                    
                    // Check if we should still process this connection
                    if (_cancellationTokenSource.Token.IsCancellationRequested)
                    {
                        try
                        {
                            context.Response.StatusCode = 503; // Service Unavailable
                            context.Response.Close();
                        }
                        catch { }
                        break;
                    }
                    
                    // Handle connection in background task with proper async/await
                    _ = Task.Run(async () =>
                    {
                        try
                        {
                            await HandleContextAsync(context, _cancellationTokenSource.Token);
                        }
                        catch (Exception ex)
                        {
                            _logger.LogError(ex, "Error in HandleContextAsync task");
                        }
                    }, _cancellationTokenSource.Token);
                }
                catch (HttpListenerException ex)
                {
                    if (_cancellationTokenSource.Token.IsCancellationRequested)
                    {
                        // Expected when shutting down
                        _logger.LogDebug("HttpListener stopped (shutdown requested)");
                        break;
                    }
                    _logger.LogWarning(ex, "HttpListener error: {Message} (ErrorCode: {ErrorCode})", 
                        ex.Message, ex.ErrorCode);
                    // Continue to accept more connections
                }
                catch (ObjectDisposedException)
                {
                    // Listener was disposed, expected during shutdown
                    _logger.LogDebug("HttpListener was disposed");
                    break;
                }
                catch (InvalidOperationException ex)
                {
                    if (_cancellationTokenSource.Token.IsCancellationRequested)
                    {
                        break;
                    }
                    _logger.LogWarning(ex, "HttpListener invalid operation: {Message}", ex.Message);
                    break;
                }
                catch (Exception ex)
                {
                    if (_cancellationTokenSource.Token.IsCancellationRequested)
                    {
                        break; // Expected when shutting down
                    }
                    _logger.LogError(ex, "Unexpected error accepting connection: {Message}", ex.Message);
                    // Wait a bit before trying again to avoid tight loop
                    try
                    {
                        await Task.Delay(1000, _cancellationTokenSource.Token);
                    }
                    catch (OperationCanceledException)
                    {
                        break;
                    }
                }
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "WebSocket server error");
            throw;
        }
        finally
        {
            // Clean up HttpListener
            try
            {
                _listener?.Stop();
                _listener?.Close();
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex, "Error stopping HttpListener");
            }
        }
    }

    private async Task HandleContextAsync(HttpListenerContext context, CancellationToken cancellationToken)
    {
        if (cancellationToken.IsCancellationRequested)
        {
            return;
        }

        if (!context.Request.IsWebSocketRequest)
        {
            try
            {
                context.Response.StatusCode = 400;
                context.Response.Close();
            }
            catch (Exception ex)
            {
                _logger.LogDebug(ex, "Error closing non-WebSocket request");
            }
            return;
        }

        WebSocketContext? wsContext = null;
        try
        {
            // Accept WebSocket connection with proper error handling
            wsContext = await context.AcceptWebSocketAsync(null);
            
            if (wsContext?.WebSocket != null)
            {
                await HandleClientAsync(wsContext.WebSocket, cancellationToken);
            }
        }
        catch (HttpListenerException ex)
        {
            _logger.LogWarning(ex, "HttpListener error during WebSocket acceptance: {Message}", ex.Message);
            try
            {
                context.Response.StatusCode = 500;
                context.Response.Close();
            }
            catch { }
        }
        catch (WebSocketException ex)
        {
            _logger.LogWarning(ex, "WebSocket error during connection: {Message}", ex.Message);
            try
            {
                context.Response.StatusCode = 500;
                context.Response.Close();
            }
            catch { }
        }
        catch (InvalidOperationException ex)
        {
            _logger.LogWarning(ex, "Invalid operation during WebSocket acceptance: {Message}", ex.Message);
            try
            {
                context.Response.StatusCode = 500;
                context.Response.Close();
            }
            catch { }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Unexpected error handling WebSocket connection");
            try
            {
                context.Response.StatusCode = 500;
                context.Response.Close();
            }
            catch { }
        }
        finally
        {
            try
            {
                wsContext?.WebSocket?.Dispose();
            }
            catch (Exception ex)
            {
                _logger.LogDebug(ex, "Error disposing WebSocket");
            }
        }
    }

    private async Task HandleClientAsync(WebSocket websocket, CancellationToken cancellationToken)
    {
        var remoteAddr = websocket.State == WebSocketState.Open 
            ? websocket.GetHashCode().ToString() 
            : "unknown";
        
        _logger.LogInformation("WebSocket client connected: {RemoteAddr}", remoteAddr);

        var connectionState = new ConnectionState
        {
            Address = remoteAddr,
            ConnectedAt = DateTime.UtcNow
        };
        _connections[websocket] = connectionState;

        try
        {
            // Handle key exchange
            await HandleKeyExchangeAsync(websocket, connectionState, cancellationToken);

            // Send connection status
            await SendConnectionStatusAsync(websocket, "connected");

            // Handle messages
            await HandleMessagesAsync(websocket, connectionState, cancellationToken);
        }
        catch (WebSocketException ex)
        {
            _logger.LogInformation("WebSocket client disconnected: {RemoteAddr} - {Error}", remoteAddr, ex.Message);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error handling client {RemoteAddr}", remoteAddr);
        }
        finally
        {
            _connections.Remove(websocket);
            try
            {
                await websocket.CloseAsync(WebSocketCloseStatus.NormalClosure, "Closing", cancellationToken);
            }
            catch { }
        }
    }

    private async Task HandleKeyExchangeAsync(WebSocket websocket, ConnectionState state, CancellationToken cancellationToken)
    {
        // Check for pre-shared key (for testing)
        var preSharedKey = Environment.GetEnvironmentVariable("APPCONNECT_PRE_SHARED_KEY");
        if (!string.IsNullOrEmpty(preSharedKey))
        {
            try
            {
                var keyBytes = Convert.FromBase64String(preSharedKey);
                if (keyBytes.Length == EncryptionManager.KeySize)
                {
                    state.Encryption = new EncryptionManager();
                    state.Encryption.SetKey(keyBytes);
                    state.KeyExchanged = true;
                    _logger.LogInformation("Using pre-shared key for {Address}", state.Address);
                    return;
                }
            }
            catch { }
        }

        // Perform RSA key exchange
        try
        {
            var buffer = new byte[4096];
            var result = await websocket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken);
            
            if (result.MessageType == WebSocketMessageType.Close)
            {
                return;
            }

            var message = Encoding.UTF8.GetString(buffer, 0, result.Count);
            var keyExchangeData = JsonSerializer.Deserialize<JsonElement>(message);

            if (keyExchangeData.TryGetProperty("type", out var type) && 
                type.GetString() == "key_exchange" &&
                keyExchangeData.TryGetProperty("encrypted_key", out var encryptedKey))
            {
                var encryptedKeyB64 = encryptedKey.GetString();
                if (string.IsNullOrEmpty(encryptedKeyB64))
                {
                    throw new InvalidOperationException("Missing encrypted_key in key exchange");
                }

                var aesKey = _keyExchange.DecryptAesKey(encryptedKeyB64);
                
                state.Encryption = new EncryptionManager();
                state.Encryption.SetKey(aesKey);
                state.KeyExchanged = true;

                // Send confirmation
                var ack = JsonSerializer.Serialize(new { type = "key_exchange_ack", status = "ok" });
                await websocket.SendAsync(
                    Encoding.UTF8.GetBytes(ack),
                    WebSocketMessageType.Text,
                    true,
                    cancellationToken);

                _logger.LogInformation("Key exchange completed for {Address}", state.Address);
            }
            else
            {
                throw new InvalidOperationException("Expected key exchange message");
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Key exchange failed");
            try
            {
                var error = JsonSerializer.Serialize(new { type = "key_exchange_ack", status = "error", message = ex.Message });
                await websocket.SendAsync(
                    Encoding.UTF8.GetBytes(error),
                    WebSocketMessageType.Text,
                    true,
                    cancellationToken);
            }
            catch { }
            throw;
        }
    }

    private async Task HandleMessagesAsync(WebSocket websocket, ConnectionState state, CancellationToken cancellationToken)
    {
        var buffer = new byte[4096];
        
        while (websocket.State == WebSocketState.Open && !cancellationToken.IsCancellationRequested)
        {
            var result = await websocket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken);
            
            if (result.MessageType == WebSocketMessageType.Close)
            {
                break;
            }

            if (result.MessageType == WebSocketMessageType.Text)
            {
                var message = Encoding.UTF8.GetString(buffer, 0, result.Count);
                await ProcessMessageAsync(websocket, message, state, cancellationToken);
            }
        }
    }

    private async Task ProcessMessageAsync(WebSocket websocket, string message, ConnectionState state, CancellationToken cancellationToken)
    {
        if (!state.KeyExchanged || state.Encryption == null)
        {
            _logger.LogWarning("Received message before key exchange");
            return;
        }

        // Check if this is a control message (plain JSON) or encrypted clipboard data
        if (!message.Contains('|'))
        {
            try
            {
                var controlMsg = JsonSerializer.Deserialize<JsonElement>(message);
                if (controlMsg.TryGetProperty("type", out _))
                {
                    await HandleControlMessageAsync(websocket, controlMsg, state);
                    return;
                }
            }
            catch { }
        }

        // This is encrypted clipboard data
        try
        {
            var decrypted = state.Encryption.DecryptFromTransmission(message);
            var clipboardItem = JsonSerializer.Deserialize<ClipboardItem>(decrypted);
            
            if (clipboardItem != null && !string.IsNullOrEmpty(clipboardItem.Content))
            {
                _logger.LogInformation("Received clipboard: {Content}...", 
                    clipboardItem.Content.Length > 50 
                        ? clipboardItem.Content.Substring(0, 50) 
                        : clipboardItem.Content);

                // Write to clipboard
                ClipboardWriter.WriteText(clipboardItem.Content);

                // Notify callback
                OnClipboardReceived?.Invoke(clipboardItem.Content);

                state.MessagesReceived++;
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing message");
        }
    }

    private async Task HandleControlMessageAsync(WebSocket websocket, JsonElement message, ConnectionState state)
    {
        if (message.TryGetProperty("type", out var type))
        {
            var typeStr = type.GetString();
            _logger.LogDebug("Received control message: {Type}", typeStr);
            // Handle control messages (error_report, connection_status, etc.)
        }
    }

    public async Task<bool> SendClipboardAsync(WebSocket websocket, string content)
    {
        if (!_connections.TryGetValue(websocket, out var state) || 
            !state.KeyExchanged || 
            state.Encryption == null)
        {
            return false;
        }

        try
        {
            var clipboardItem = new ClipboardItem
            {
                Content = content,
                SourceDeviceId = _config.DeviceName,
                Timestamp = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
                Hash = EncryptionManager.CalculateHash(content),
                Ttl = 86400000 // 24 hours
            };

            var json = JsonSerializer.Serialize(clipboardItem);
            var encrypted = state.Encryption.EncryptForTransmission(json);

            var bytes = Encoding.UTF8.GetBytes(encrypted);
            await websocket.SendAsync(
                new ArraySegment<byte>(bytes),
                WebSocketMessageType.Text,
                true,
                CancellationToken.None);

            state.MessagesSent++;
            return true;
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error sending clipboard");
            return false;
        }
    }

    public async Task<int> BroadcastClipboardAsync(string content)
    {
        var count = 0;
        var tasks = _connections.Keys
            .Where(ws => ws.State == WebSocketState.Open)
            .Select(async ws =>
            {
                if (await SendClipboardAsync(ws, content))
                {
                    Interlocked.Increment(ref count);
                }
            });

        await Task.WhenAll(tasks);
        return count;
    }

    private async Task SendConnectionStatusAsync(WebSocket websocket, string status)
    {
        if (!_connections.TryGetValue(websocket, out var state))
        {
            return;
        }

        try
        {
            var statusMsg = JsonSerializer.Serialize(new
            {
                type = "connection_status",
                status = status,
                timestamp = DateTimeOffset.UtcNow.ToUnixTimeSeconds()
            });

            var bytes = Encoding.UTF8.GetBytes(statusMsg);
            await websocket.SendAsync(
                new ArraySegment<byte>(bytes),
                WebSocketMessageType.Text,
                true,
                CancellationToken.None);
        }
        catch { }
    }

    private class ConnectionState
    {
        public string Address { get; set; } = string.Empty;
        public EncryptionManager? Encryption { get; set; }
        public bool KeyExchanged { get; set; }
        public DateTime ConnectedAt { get; set; }
        public int MessagesSent { get; set; }
        public int MessagesReceived { get; set; }
    }
}

public class ClipboardItem
{
    public string Content { get; set; } = string.Empty;
    public string SourceDeviceId { get; set; } = string.Empty;
    public long Timestamp { get; set; }
    public string Hash { get; set; } = string.Empty;
    public long Ttl { get; set; }
}
