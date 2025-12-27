# Error Codes Explained

## Zsh Error Codes 182 and 179

### What They Mean

In zsh/bash, exit codes 128-255 indicate that a process was terminated by a signal:
- **182** = 128 + 54 (Real-time signal or SIGRTMAX-10)
- **179** = 128 + 51 (Real-time signal or SIGRTMAX-17)

These typically indicate:
1. **Unhandled Exception**: The process crashed due to an unhandled exception
2. **Signal Termination**: The process was killed by a system signal (SIGABRT, SIGSEGV, etc.)
3. **.NET Runtime Crash**: The .NET runtime encountered a fatal error
4. **Permission/Access Violation**: The process tried to access resources it doesn't have permission for

### Common Causes in This Project

1. **HttpListener Permission Issues (macOS/Linux)**
   - HttpListener may require elevated permissions for ports <= 1024
   - Solution: Use a port > 1024 or run with appropriate permissions

2. **Async Lambda Bugs**
   - Calling async methods from synchronous lambdas can cause exceptions to be swallowed
   - Fixed: Now using proper async lambdas with error handling

3. **Task.Delay(-1) Exception**
   - Negative delay throws ArgumentOutOfRangeException
   - Fixed: Now using Task.Delay(Timeout.Infinite, cancellationToken)

4. **Unhandled WebSocket Exceptions**
   - Exceptions in WebSocket handling weren't being caught properly
   - Fixed: Added comprehensive exception handling

### How to Diagnose

1. **Check Logs**: Look for error messages before the crash
2. **Run with Verbose Logging**: Check if there are any warnings
3. **Test Port Binding**: Try a different port (> 1024)
4. **Check Permissions**: Ensure the process has necessary permissions

### Solutions Applied

✅ Fixed async lambda bug (proper async/await)
✅ Fixed Task.Delay(-1) issue
✅ Added comprehensive HttpListener error handling
✅ Added macOS/Linux port permission warnings
✅ Added proper exception handling for WebSocket operations
✅ Added cancellation token support throughout

### Testing After Fixes

```bash
# Test with default port
dotnet run

# Test with non-privileged port (recommended for macOS)
export APPCONNECT_PORT=8766
dotnet run
```

If you still see error codes 182/179, check:
1. Console output for specific error messages
2. System logs (macOS: Console.app, Linux: journalctl)
3. .NET runtime logs
