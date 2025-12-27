using System.Security.Cryptography;
using System.Text;
using Microsoft.Extensions.Logging;

namespace AppConnectServer.Core;

/// <summary>
/// AES-256-GCM encryption manager matching Android implementation.
/// </summary>
public class EncryptionManager
{
    private readonly ILogger<EncryptionManager>? _logger;
    private byte[]? _key;
    private AesGcm? _aesGcm;

    public const int KeySize = 32; // 256 bits
    public const int IvSize = 12;   // 96 bits
    public const int TagSize = 16;  // 128 bits

    public EncryptionManager(ILoggerFactory? loggerFactory = null)
    {
        _logger = loggerFactory?.CreateLogger<EncryptionManager>();
    }

    public void SetKey(byte[] key)
    {
        if (key == null || key.Length != KeySize)
        {
            throw new ArgumentException($"AES key must be {KeySize} bytes (256 bits), got {key?.Length ?? 0} bytes");
        }

        _key = key;
        _aesGcm = new AesGcm(key);
    }

    public byte[] GenerateKey()
    {
        var key = new byte[KeySize];
        RandomNumberGenerator.Fill(key);
        SetKey(key);
        return key;
    }

    public (byte[] iv, byte[] encrypted) Encrypt(string data)
    {
        if (_aesGcm == null)
        {
            throw new InvalidOperationException("Encryption key not set");
        }

        if (string.IsNullOrEmpty(data))
        {
            throw new ArgumentException("Data cannot be null or empty", nameof(data));
        }

        // Generate random 12-byte IV
        var iv = new byte[IvSize];
        RandomNumberGenerator.Fill(iv);

        // Encrypt data (GCM automatically appends 16-byte tag)
        var plaintext = Encoding.UTF8.GetBytes(data);
        var ciphertext = new byte[plaintext.Length + TagSize];
        _aesGcm.Encrypt(iv, plaintext, ciphertext, null);

        return (iv, ciphertext);
    }

    public string Decrypt(byte[] iv, byte[] encrypted)
    {
        if (_aesGcm == null)
        {
            throw new InvalidOperationException("Encryption key not set");
        }

        if (iv == null || iv.Length != IvSize)
        {
            throw new ArgumentException($"IV must be {IvSize} bytes, got {iv?.Length ?? 0} bytes");
        }

        if (encrypted == null || encrypted.Length < TagSize)
        {
            throw new ArgumentException($"Encrypted data too short (must include {TagSize}-byte tag)");
        }

        try
        {
            var plaintext = new byte[encrypted.Length - TagSize];
            _aesGcm.Decrypt(iv, encrypted, plaintext, null);
            return Encoding.UTF8.GetString(plaintext);
        }
        catch (CryptographicException ex)
        {
            throw new InvalidOperationException("Decryption failed: authentication tag verification failed", ex);
        }
    }

    public string EncryptForTransmission(string data)
    {
        var (iv, encrypted) = Encrypt(data);

        // Base64 encode with NO_WRAP (WITH padding, no line breaks)
        // Android's Base64.NO_WRAP includes padding, so we must too
        var ivB64 = Convert.ToBase64String(iv);
        var encryptedB64 = Convert.ToBase64String(encrypted);

        return $"{ivB64}|{encryptedB64}";
    }

    public string DecryptFromTransmission(string message)
    {
        if (string.IsNullOrEmpty(message))
        {
            throw new ArgumentException("Message cannot be null or empty", nameof(message));
        }

        var parts = message.Split('|', 2);
        if (parts.Length != 2)
        {
            throw new ArgumentException("Invalid message format: expected {ivBase64}|{encryptedBase64}");
        }

        var ivB64 = parts[0];
        var encryptedB64 = parts[1];

        if (string.IsNullOrEmpty(ivB64) || string.IsNullOrEmpty(encryptedB64))
        {
            throw new ArgumentException("Invalid message format: IV or encrypted data is empty");
        }

        try
        {
            // Base64 decode - add padding if needed
            // Android uses NO_WRAP which strips padding
            var iv = Convert.FromBase64String(AddPadding(ivB64));
            var encrypted = Convert.FromBase64String(AddPadding(encryptedB64));

            if (iv.Length != IvSize)
            {
                throw new ArgumentException($"Decoded IV size is {iv.Length} bytes, expected {IvSize} bytes");
            }

            return Decrypt(iv, encrypted);
        }
        catch (FormatException ex)
        {
            throw new ArgumentException("Invalid base64 encoding", ex);
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

    public static string CalculateHash(string content)
    {
        if (string.IsNullOrEmpty(content))
        {
            throw new ArgumentException("Content cannot be null or empty", nameof(content));
        }

        var bytes = Encoding.UTF8.GetBytes(content);
        var hash = SHA256.HashData(bytes);
        return Convert.ToHexString(hash).ToLowerInvariant();
    }
}
