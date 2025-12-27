using System.Threading;
using Microsoft.Extensions.Logging;
using AppConnectServer.Core;

namespace AppConnectServer.Clipboard;

public class ClipboardMonitor
{
    private readonly ILogger<ClipboardMonitor> _logger;
    private readonly Action<string> _onChange;
    private readonly int _debounceMs;
    private string? _lastContent;
    private string? _lastHash;
    private bool _running;
    private Thread? _monitorThread;
    private readonly object _lock = new();

    public ClipboardMonitor(
        ILoggerFactory loggerFactory,
        Action<string> onChange,
        int debounceMs = 500)
    {
        _logger = loggerFactory.CreateLogger<ClipboardMonitor>();
        _onChange = onChange;
        _debounceMs = debounceMs;
    }

    public void Start()
    {
        if (_running) return;

        _running = true;
        _monitorThread = new Thread(MonitorLoop)
        {
            IsBackground = true
        };
        _monitorThread.Start();
        _logger.LogInformation("Clipboard monitoring started");
    }

    public void Stop()
    {
        _running = false;
        _monitorThread?.Join(TimeSpan.FromSeconds(1));
        _logger.LogInformation("Clipboard monitoring stopped");
    }

    private void MonitorLoop()
    {
        while (_running)
        {
            try
            {
                var currentContent = ClipboardWriter.GetCurrentText();
                
                if (!string.IsNullOrEmpty(currentContent) && currentContent != _lastContent)
                {
                    var currentHash = EncryptionManager.CalculateHash(currentContent);
                    
                    if (currentHash != _lastHash)
                    {
                        lock (_lock)
                        {
                            _lastContent = currentContent;
                            _lastHash = currentHash;
                        }

                        // Debounce: wait a bit before notifying
                        Thread.Sleep(_debounceMs);

                        // Check if content still changed after debounce
                        if (ClipboardWriter.GetCurrentText() == currentContent)
                        {
                            _onChange(currentContent);
                        }
                    }
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error monitoring clipboard");
            }

            Thread.Sleep(100); // Check every 100ms
        }
    }

    public string? GetCurrentContent()
    {
        return ClipboardWriter.GetCurrentText();
    }

    public void SetContent(string content)
    {
        lock (_lock)
        {
            _lastContent = content;
            _lastHash = EncryptionManager.CalculateHash(content);
            ClipboardWriter.WriteText(content);
        }
    }
}
