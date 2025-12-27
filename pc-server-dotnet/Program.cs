using AppConnectServer;
using Microsoft.Extensions.Logging;
using System.Threading;

// Configure logging
using var loggerFactory = LoggerFactory.Create(builder =>
{
    builder
        .AddConsole()
        .SetMinimumLevel(LogLevel.Information);
});

var logger = loggerFactory.CreateLogger<Program>();

logger.LogInformation("Starting AppConnect Server...");

// Handle Ctrl+C gracefully
var cancellationTokenSource = new CancellationTokenSource();
Console.CancelKeyPress += (sender, e) =>
{
    e.Cancel = true;
    logger.LogInformation("Shutdown signal received, stopping server...");
    cancellationTokenSource.Cancel();
};

try
{
    var server = new AppConnectServer.Server(loggerFactory);
    
    // Run server in a task so we can handle cancellation
    var serverTask = server.RunAsync();
    
    // Wait for either server completion or cancellation
    var cancellationTask = Task.Run(async () =>
    {
        await Task.Delay(Timeout.Infinite, cancellationTokenSource.Token);
    }, cancellationTokenSource.Token);
    
    await Task.WhenAny(serverTask, cancellationTask);
    
    if (cancellationTokenSource.Token.IsCancellationRequested)
    {
        logger.LogInformation("Shutdown requested, waiting for server to stop...");
        // Give server time to shut down gracefully
        await Task.Delay(2000);
    }
}
catch (OperationCanceledException)
{
    logger.LogInformation("Server shutdown completed");
}
catch (Exception ex)
{
    logger.LogError(ex, "Fatal error occurred");
    Environment.Exit(1);
}
