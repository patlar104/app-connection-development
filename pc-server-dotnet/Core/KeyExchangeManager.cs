using System.Linq;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Logging;

namespace AppConnectServer.Core;

/// <summary>
/// RSA-based key exchange for establishing shared AES keys.
/// </summary>
public class KeyExchangeManager
{
    private readonly ILogger<KeyExchangeManager>? _logger;
    private RSA? _rsaPrivateKey;
    private readonly string _privateKeyFile;

    public const int DefaultKeySize = 2048;
    public const int MinKeySize = 1024;
    public const int MaxKeySize = 4096;

    public KeyExchangeManager(string privateKeyFile, ILoggerFactory? loggerFactory = null)
    {
        _privateKeyFile = privateKeyFile;
        _logger = loggerFactory?.CreateLogger<KeyExchangeManager>();

        if (File.Exists(privateKeyFile))
        {
            LoadPrivateKey();
        }
    }

    public void LoadPrivateKey()
    {
        if (!File.Exists(_privateKeyFile))
        {
            throw new FileNotFoundException($"RSA private key file not found: {_privateKeyFile}");
        }

        try
        {
            var keyData = File.ReadAllText(_privateKeyFile);
            if (string.IsNullOrEmpty(keyData))
            {
                throw new InvalidOperationException($"RSA private key file is empty: {_privateKeyFile}");
            }

            _rsaPrivateKey = RSA.Create();
            _rsaPrivateKey.ImportFromPem(keyData);

            var keySize = _rsaPrivateKey.KeySize;
            if (keySize < MinKeySize)
            {
                throw new InvalidOperationException(
                    $"RSA key size {keySize} bits is too small (minimum: {MinKeySize} bits)");
            }

            _logger?.LogInformation("Loaded RSA private key from {File} (key size: {KeySize} bits)",
                _privateKeyFile, keySize);
        }
        catch (Exception ex)
        {
            throw new InvalidOperationException($"Failed to load RSA private key: {ex.Message}", ex);
        }
    }

    public (RSA PrivateKey, RSA PublicKey) GenerateKeyPair(int keySize = DefaultKeySize)
    {
        if (keySize < MinKeySize || keySize > MaxKeySize)
        {
            throw new ArgumentException(
                $"RSA key size must be between {MinKeySize} and {MaxKeySize} bits");
        }

        _logger?.LogInformation("Generating RSA key pair (key size: {KeySize} bits)...", keySize);

        var privateKey = RSA.Create(keySize);
        var publicKeyParams = privateKey.ExportParameters(false);
        var publicKey = RSA.Create(publicKeyParams);

        _logger?.LogInformation("RSA key pair generated successfully");

        return (privateKey, publicKey);
    }

    public string GetPublicKeyPem()
    {
        if (_rsaPrivateKey == null)
        {
            throw new InvalidOperationException("RSA private key not loaded");
        }

        var publicKeyParams = _rsaPrivateKey.ExportParameters(false);
        using var publicKey = RSA.Create(publicKeyParams);

        return publicKey.ExportRSAPublicKeyPem();
    }

    public string GetPublicKeyBase64()
    {
        var pem = GetPublicKeyPem();
        // Remove PEM headers and newlines
        var lines = pem.Split('\n')
            .Where(line => !line.Contains("BEGIN") && !line.Contains("END") && !string.IsNullOrWhiteSpace(line))
            .ToArray();
        return string.Join("", lines);
    }

    public byte[] DecryptAesKey(string encryptedKeyB64)
    {
        if (_rsaPrivateKey == null)
        {
            throw new InvalidOperationException("RSA private key not loaded");
        }

        try
        {
            // Add padding for Base64 decode (Android uses NO_WRAP which strips padding)
            var encryptedKey = Convert.FromBase64String(AddPadding(encryptedKeyB64));

            _logger?.LogDebug("Decrypting AES key: encrypted length={Length} bytes", encryptedKey.Length);

            // Decrypt using RSA-OAEP with SHA-256
            var aesKey = _rsaPrivateKey.Decrypt(encryptedKey, RSAEncryptionPadding.OaepSHA256);

            // Validate decrypted AES key size (must be 32 bytes for AES-256)
            if (aesKey.Length != EncryptionManager.KeySize)
            {
                throw new ArgumentException(
                    $"Invalid AES key size: {aesKey.Length} bytes (expected {EncryptionManager.KeySize} bytes)");
            }

            _logger?.LogInformation("AES key decrypted successfully");
            return aesKey;
        }
        catch (FormatException ex)
        {
            throw new ArgumentException($"Invalid base64 encoding: {ex.Message}", ex);
        }
        catch (CryptographicException ex)
        {
            throw new InvalidOperationException($"RSA decryption failed: {ex.Message}", ex);
        }
    }

    private static string AddPadding(string s)
    {
        var missingPadding = s.Length % 4;
        if (missingPadding != 0)
        {
            return s + new string('=', 4 - missingPadding);
        }
        return s;
    }
}
