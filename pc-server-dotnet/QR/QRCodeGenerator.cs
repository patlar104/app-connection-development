using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using Microsoft.Extensions.Logging;
using QRCoder;
using AppConnectServer.Core;

namespace AppConnectServer.QR;

public class QRCodeGenerator
{
    private readonly Configuration _config;
    private readonly string _localIp;
    private readonly string _certFingerprint;
    private readonly ILogger<QRCodeGenerator> _logger;
    private readonly KeyExchangeManager _keyExchange;

    public QRCodeGenerator(
        Configuration config,
        string localIp,
        string certFingerprint,
        ILoggerFactory loggerFactory)
    {
        _config = config;
        _localIp = localIp;
        _certFingerprint = certFingerprint;
        _logger = loggerFactory.CreateLogger<QRCodeGenerator>();
        _keyExchange = new KeyExchangeManager(_config.RsaKeyFile, loggerFactory);
    }

    public void EnsureRsaKeyExists()
    {
        if (!File.Exists(_config.RsaKeyFile))
        {
            _logger.LogInformation("Generating RSA key pair...");
            var (privateKey, publicKey) = _keyExchange.GenerateKeyPair();
            
            // Save private key
            var privateKeyPem = ExportPrivateKeyPem(privateKey);
            File.WriteAllText(_config.RsaKeyFile, privateKeyPem);
            
            // Save public key
            var publicKeyPem = ExportPublicKeyPem(publicKey);
            File.WriteAllText(_config.RsaPublicKeyFile, publicKeyPem);
            
            _logger.LogInformation("RSA key pair generated and saved");
            
            // Clean up
            privateKey.Dispose();
            publicKey.Dispose();
        }
        else
        {
            _keyExchange.LoadPrivateKey();
        }
    }

    public void DisplayQRCode()
    {
        var connectionInfo = GenerateConnectionInfo();
        _logger.LogInformation("Connection info: {Info}", connectionInfo);

        // Generate QR code
        using var qrGenerator = new QRCodeGenerator();
        var qrData = qrGenerator.CreateQrCode(connectionInfo, QRCodeGenerator.ECCLevel.Q);
        using var qrCode = new AsciiQRCode(qrData);
        var qrAscii = qrCode.GetGraphic(1);

        // Display in console
        Console.WriteLine("\n=== AppConnect QR Code ===");
        Console.WriteLine(qrAscii);
        Console.WriteLine("=== Connection Info ===");
        Console.WriteLine(connectionInfo);
        Console.WriteLine("======================\n");

        // Save QR code image
        try
        {
            using var qrBitmap = new BitmapByteQRCode(qrData);
            var qrBytes = qrBitmap.GetGraphic(20);
            var qrPath = Path.Combine(_config.CertDir, "qr_code.png");
            File.WriteAllBytes(qrPath, qrBytes);
            _logger.LogInformation("QR code saved to: {Path}", qrPath);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to save QR code image");
        }
    }

    private string GenerateConnectionInfo()
    {
        var publicKeyB64 = _keyExchange.GetPublicKeyBase64();
        
        var connectionInfo = new
        {
            n = _config.DeviceName,
            ip = _localIp,
            p = _config.Port,
            k = publicKeyB64,
            fp = _certFingerprint
        };

        return JsonSerializer.Serialize(connectionInfo, new JsonSerializerOptions
        {
            WriteIndented = false
        });
    }

    private static string ExportPrivateKeyPem(RSA rsa)
    {
        var parameters = rsa.ExportRSAPrivateKey();
        var builder = new StringBuilder();
        builder.AppendLine("-----BEGIN RSA PRIVATE KEY-----");
        builder.AppendLine(Convert.ToBase64String(
            parameters,
            Base64FormattingOptions.InsertLineBreaks));
        builder.AppendLine("-----END RSA PRIVATE KEY-----");
        return builder.ToString();
    }

    private static string ExportPublicKeyPem(RSA publicKey)
    {
        var parameters = publicKey.ExportRSAPublicKey();
        var builder = new StringBuilder();
        builder.AppendLine("-----BEGIN PUBLIC KEY-----");
        builder.AppendLine(Convert.ToBase64String(
            parameters,
            Base64FormattingOptions.InsertLineBreaks));
        builder.AppendLine("-----END PUBLIC KEY-----");
        return builder.ToString();
    }
}
