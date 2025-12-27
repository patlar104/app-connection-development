using AppConnectServer;
using Microsoft.Extensions.Logging;

// Configure logging
using var loggerFactory = LoggerFactory.Create(builder =>
{
    builder
        .AddConsole()
        .SetMinimumLevel(LogLevel.Information);
});

var logger = loggerFactory.CreateLogger<Program>();

logger.LogInformation("Starting AppConnect Server...");

try
{
    var server = new AppConnectServer.Server(loggerFactory);
    await server.RunAsync();
}
catch (Exception ex)
{
    logger.LogError(ex, "Fatal error occurred");
    Environment.Exit(1);
}
