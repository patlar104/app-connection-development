using System.Net;
using System.Threading;
using Microsoft.Extensions.Logging;
using AppConnectServer.Core;
using AppConnectServer.Network;
using AppConnectServer.Clipboard;
using AppConnectServer.Certificate;
using AppConnectServer.Discovery;
using AppConnectServer.QR;

namespace AppConnectServer;

public class Server
{
    private readonly ILoggerFactory _loggerFactory;
    private readonly ILogger<Server> _logger;
    private readonly Configuration _config;
    private WebSocketServer? _webSocketServer;
    private ClipboardMonitor? _clipboardMonitor;
    private MDnsService? _mdnsService;
    private CertificateManager? _certManager;
    private QRCodeGenerator? _qrGenerator;

    public Server(ILoggerFactory loggerFactory)
    {
        _loggerFactory = loggerFactory;
        _logger = loggerFactory.CreateLogger<Server>();
        _config = new Configuration();
    }

    public async Task RunAsync()
    {
        try
        {
            Initialize();
            await StartAsync();
            
            // Keep server running - StartAsync will block until server stops
            // The WebSocket server's StartAsync method contains the main loop
            // So we just wait here (this should never complete unless there's an error)
            await Task.Delay(Timeout.Infinite);
        }
        catch (OperationCanceledException)
        {
            // Expected when cancellation is requested
            _logger.LogInformation("Server shutdown requested");
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Server error");
            throw;
        }
        finally
        {
            await StopAsync();
        }
    }

    private void Initialize()
    {
        _logger.LogInformation("Initializing AppConnect server...");

        // Initialize certificate manager
        _certManager = new CertificateManager(_config, _loggerFactory);
        _certManager.EnsureCertificateExists();

        // Get local IP
        var localIp = GetLocalIpAddress();
        if (localIp == null)
        {
            throw new InvalidOperationException("Could not determine local IP address");
        }

        _logger.LogInformation("Local IP: {LocalIp}", localIp);

        // Get certificate fingerprint
        var certFingerprint = _certManager.GetCertificateFingerprint();
        _logger.LogInformation("Certificate fingerprint: {Fingerprint}", certFingerprint);

        // Initialize QR code generator
        _qrGenerator = new QRCodeGenerator(
            _config,
            localIp,
            certFingerprint,
            _loggerFactory
        );
        _qrGenerator.EnsureRsaKeyExists();
        _qrGenerator.DisplayQRCode();

        // Initialize mDNS service
        _mdnsService = new MDnsService(_loggerFactory);

        // Initialize WebSocket server
        var certificate = _certManager.GetCertificate();
        _webSocketServer = new WebSocketServer(
            certificate,
            _config,
            _loggerFactory,
            OnClipboardReceived
        );

        // Initialize clipboard monitor
        _clipboardMonitor = new ClipboardMonitor(
            _loggerFactory,
            OnClipboardChanged,
            debounceMs: 500
        );

        _logger.LogInformation("Server initialization complete");
    }

    private async Task StartAsync()
    {
        _logger.LogInformation("Starting server services...");

        // Start mDNS broadcasting
        var localIp = GetLocalIpAddress();
        if (localIp != null && _mdnsService != null)
        {
            await _mdnsService.StartAsync(_config.DeviceName, _config.Port, localIp);
        }

        // Start clipboard monitoring
        _clipboardMonitor?.Start();

        // Start WebSocket server (this blocks)
        if (_webSocketServer != null)
        {
            await _webSocketServer.StartAsync("0.0.0.0", _config.Port);
        }
    }

    private async Task StopAsync()
    {
        _logger.LogInformation("Stopping server...");

        _clipboardMonitor?.Stop();

        // Stop WebSocket server
        _webSocketServer?.Stop();

        if (_mdnsService != null)
        {
            await _mdnsService.StopAsync();
        }

        _logger.LogInformation("Server stopped");
    }

    private void OnClipboardReceived(string content)
    {
        _logger.LogInformation("Clipboard received from Android: {Content}...", 
            content.Length > 50 ? content.Substring(0, 50) : content);
        
        // Update monitor to prevent loop
        _clipboardMonitor?.SetContent(content);
    }

    private void OnClipboardChanged(string content)
    {
        if (_webSocketServer == null) return;

        _logger.LogInformation("Local clipboard changed: {Content}...", 
            content.Length > 50 ? content.Substring(0, 50) : content);

        // Broadcast to all connected clients
        // Use Task.Run with proper error handling
        _ = Task.Run(async () =>
        {
            try
            {
                await _webSocketServer.BroadcastClipboardAsync(content);
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error broadcasting clipboard change");
            }
        });
    }

    private string? GetLocalIpAddress()
    {
        try
        {
            // Try to get IP from default gateway interface
            var host = Dns.GetHostEntry(Dns.GetHostName());
            foreach (var ip in host.AddressList)
            {
                if (ip.AddressFamily == System.Net.Sockets.AddressFamily.InterNetwork)
                {
                    // Prefer non-loopback addresses
                    if (!IPAddress.IsLoopback(ip))
                    {
                        return ip.ToString();
                    }
                }
            }

            // Fallback: try connecting to external address
            using var socket = new System.Net.Sockets.Socket(
                System.Net.Sockets.AddressFamily.InterNetwork,
                System.Net.Sockets.SocketType.Dgram,
                0
            );
            socket.Connect("8.8.8.8", 65530);
            var endPoint = socket.LocalEndPoint as IPEndPoint;
            return endPoint?.Address.ToString();
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to determine local IP address");
            return null;
        }
    }
}
