namespace AppConnectServer;

public class Configuration
{
    public int Port { get; }
    public string DeviceName { get; }
    public string CertFile { get; }
    public string KeyFile { get; }
    public string RsaKeyFile { get; }
    public string RsaPublicKeyFile { get; }
    public string CertDir { get; }

    public Configuration()
    {
        Port = int.Parse(Environment.GetEnvironmentVariable("APPCONNECT_PORT") ?? "8765");
        DeviceName = Environment.GetEnvironmentVariable("APPCONNECT_DEVICE_NAME") ?? "My-PC";
        
        // Create certs directory
        CertDir = Path.Combine(AppContext.BaseDirectory, "certs");
        Directory.CreateDirectory(CertDir);
        
        CertFile = Path.Combine(CertDir, "server.crt");
        KeyFile = Path.Combine(CertDir, "server.key");
        RsaKeyFile = Path.Combine(CertDir, "rsa_private.pem");
        RsaPublicKeyFile = Path.Combine(CertDir, "rsa_public.pem");
    }
}
