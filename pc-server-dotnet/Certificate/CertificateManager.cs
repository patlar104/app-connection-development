using System.Net;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using Microsoft.Extensions.Logging;

namespace AppConnectServer.Certificate;

public class CertificateManager
{
    private readonly Configuration _config;
    private readonly ILogger<CertificateManager> _logger;
    private X509Certificate2? _certificate;

    public CertificateManager(Configuration config, ILoggerFactory loggerFactory)
    {
        _config = config;
        _logger = loggerFactory.CreateLogger<CertificateManager>();
    }

    public void EnsureCertificateExists()
    {
        if (File.Exists(_config.CertFile) && File.Exists(_config.KeyFile))
        {
            LoadCertificate();
        }
        else
        {
            GenerateCertificate();
        }
    }

    private void GenerateCertificate()
    {
        _logger.LogInformation("Generating SSL certificate...");

        var hostname = Dns.GetHostName();
        
        // Generate RSA key
        using var rsa = RSA.Create(2048);
        
        // Create certificate request
        var request = new CertificateRequest(
            $"CN={hostname}, O=AppConnect, L=Local, ST=Local, C=US",
            rsa,
            HashAlgorithmName.SHA256,
            RSASignaturePadding.Pkcs1);

        // Add extensions
        request.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(false, false, 0, false));
        
        request.CertificateExtensions.Add(
            new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
                false));

        var sanBuilder = new SubjectAlternativeNameBuilder();
        sanBuilder.AddDnsName(hostname);
        sanBuilder.AddDnsName("localhost");
        sanBuilder.AddIpAddress(IPAddress.Loopback);
        request.CertificateExtensions.Add(sanBuilder.Build());

        // Create self-signed certificate (valid for 10 years)
        var certificate = request.CreateSelfSigned(
            DateTimeOffset.UtcNow,
            DateTimeOffset.UtcNow.AddYears(10));

        // Save certificate
        File.WriteAllText(_config.CertFile, 
            ExportCertificatePem(certificate));
        
        // Save private key
        File.WriteAllText(_config.KeyFile, 
            ExportPrivateKeyPem(rsa));

        _certificate = certificate;
        _logger.LogInformation("Generated SSL certificate: {CertFile}", _config.CertFile);
    }

    private void LoadCertificate()
    {
        try
        {
            var certPem = File.ReadAllText(_config.CertFile);
            var keyPem = File.ReadAllText(_config.KeyFile);
            
            _certificate = X509Certificate2.CreateFromPem(certPem, keyPem);
            _logger.LogInformation("Loaded existing certificate from {CertFile}", _config.CertFile);
        }
        catch (Exception ex)
        {
            _logger.LogWarning(ex, "Failed to load certificate, regenerating...");
            GenerateCertificate();
        }
    }

    public string GetCertificateFingerprint()
    {
        if (_certificate == null)
        {
            LoadCertificate();
        }

        if (_certificate == null)
        {
            throw new InvalidOperationException("Certificate not available");
        }

        // Calculate SHA-256 fingerprint
        var hash = SHA256.HashData(_certificate.RawData);
        var hex = Convert.ToHexString(hash).ToUpperInvariant();
        
        return $"SHA256:{hex}";
    }

    public X509Certificate2? GetCertificate()
    {
        if (_certificate == null)
        {
            LoadCertificate();
        }
        return _certificate;
    }

    private static string ExportCertificatePem(X509Certificate2 certificate)
    {
        var builder = new StringBuilder();
        builder.AppendLine("-----BEGIN CERTIFICATE-----");
        builder.AppendLine(Convert.ToBase64String(
            certificate.RawData,
            Base64FormattingOptions.InsertLineBreaks));
        builder.AppendLine("-----END CERTIFICATE-----");
        return builder.ToString();
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
}
