using System;
using System.Runtime.InteropServices;
using Microsoft.Extensions.Logging;

namespace AppConnectServer.Clipboard;

public static class ClipboardWriter
{
    private static readonly ILogger? _logger;

    static ClipboardWriter()
    {
        // Initialize logger if available
        // In a real implementation, you'd inject this
    }

    public static bool WriteText(string text)
    {
        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                return WriteTextWindows(text);
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                return WriteTextMacOS(text);
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                return WriteTextLinux(text);
            }
            else
            {
                throw new PlatformNotSupportedException("Unsupported platform");
            }
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error writing to clipboard");
            return false;
        }
    }

    public static string? GetCurrentText()
    {
        try
        {
            if (RuntimeInformation.IsOSPlatform(OSPlatform.Windows))
            {
                return GetTextWindows();
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.OSX))
            {
                return GetTextMacOS();
            }
            else if (RuntimeInformation.IsOSPlatform(OSPlatform.Linux))
            {
                return GetTextLinux();
            }
            else
            {
                throw new PlatformNotSupportedException("Unsupported platform");
            }
        }
        catch (Exception ex)
        {
            _logger?.LogError(ex, "Error reading from clipboard");
            return null;
        }
    }

    private static bool WriteTextWindows(string text)
    {
        // Windows implementation using pbcopy equivalent
        // For now, use a simple approach - in production, use Windows APIs
        return WriteTextUnix(text);
    }

    private static bool WriteTextMacOS(string text)
    {
        return WriteTextUnix(text);
    }

    private static bool WriteTextLinux(string text)
    {
        return WriteTextUnix(text);
    }

    private static bool WriteTextUnix(string text)
    {
        try
        {
            var process = new System.Diagnostics.Process
            {
                StartInfo = new System.Diagnostics.ProcessStartInfo
                {
                    FileName = "pbcopy", // Works on macOS
                    Arguments = "",
                    RedirectStandardInput = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }
            };

            // Try pbcopy (macOS)
            try
            {
                process.Start();
                process.StandardInput.Write(text);
                process.StandardInput.Close();
                process.WaitForExit();
                return process.ExitCode == 0;
            }
            catch
            {
                // Try xclip (Linux)
                process.StartInfo.FileName = "xclip";
                process.StartInfo.Arguments = "-selection clipboard";
                process.Start();
                process.StandardInput.Write(text);
                process.StandardInput.Close();
                process.WaitForExit();
                return process.ExitCode == 0;
            }
        }
        catch
        {
            return false;
        }
    }

    private static string? GetTextWindows()
    {
        return GetTextUnix();
    }

    private static string? GetTextMacOS()
    {
        return GetTextUnix();
    }

    private static string? GetTextLinux()
    {
        return GetTextUnix();
    }

    private static string? GetTextUnix()
    {
        try
        {
            // Try pbpaste (macOS)
            try
            {
                var process = new System.Diagnostics.Process
                {
                    StartInfo = new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "pbpaste",
                        RedirectStandardOutput = true,
                        UseShellExecute = false,
                        CreateNoWindow = true
                    }
                };
                process.Start();
                var output = process.StandardOutput.ReadToEnd();
                process.WaitForExit();
                return process.ExitCode == 0 ? output : null;
            }
            catch
            {
                // Try xclip (Linux)
                var process = new System.Diagnostics.Process
                {
                    StartInfo = new System.Diagnostics.ProcessStartInfo
                    {
                        FileName = "xclip",
                        Arguments = "-selection clipboard -o",
                        RedirectStandardOutput = true,
                        UseShellExecute = false,
                        CreateNoWindow = true
                    }
                };
                process.Start();
                var output = process.StandardOutput.ReadToEnd();
                process.WaitForExit();
                return process.ExitCode == 0 ? output : null;
            }
        }
        catch
        {
            return null;
        }
    }
}
