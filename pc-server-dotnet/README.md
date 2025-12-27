# AppConnect PC Server (.NET)

Cross-platform PC server for AppConnect Android app, built with .NET 8.0.

## Features

- ✅ Cross-platform (Windows, macOS, Linux)
- ✅ Secure WebSocket server
- ✅ AES-256-GCM encryption
- ✅ RSA key exchange
- ✅ QR code generation for easy pairing
- ✅ Clipboard monitoring and synchronization
- ✅ mDNS service discovery

## Prerequisites

### macOS

1. **Install .NET 8.0 SDK**
   ```bash
   # Using Homebrew (recommended)
   brew install --cask dotnet-sdk
   
   # Or download from: https://dotnet.microsoft.com/download/dotnet/8.0
   ```

2. **Verify Installation**
   ```bash
   dotnet --version
   # Should show: 8.0.x or higher
   ```

3. **Install pbcopy/pbpaste** (usually pre-installed on macOS)
   ```bash
   which pbcopy
   # Should show: /usr/bin/pbcopy
   ```

### Windows

1. **Install .NET 8.0 SDK**
   - Download from: https://dotnet.microsoft.com/download/dotnet/8.0
   - Run the installer and follow the prompts

2. **Verify Installation**
   ```powershell
   dotnet --version
   ```

### Linux

1. **Install .NET 8.0 SDK**
   ```bash
   # Ubuntu/Debian
   wget https://dot.net/v1/dotnet-install.sh
   chmod +x ./dotnet-install.sh
   ./dotnet-install.sh --channel 8.0
   
   # Or use package manager
   sudo apt-get update
   sudo apt-get install -y dotnet-sdk-8.0
   ```

2. **Install xclip** (for clipboard support)
   ```bash
   sudo apt-get install -y xclip
   ```

## Building

1. **Navigate to project directory**
   ```bash
   cd pc-server-dotnet
   ```

2. **Restore dependencies**
   ```bash
   dotnet restore
   ```

3. **Build the project**
   ```bash
   dotnet build
   ```

4. **Run the server**
   ```bash
   dotnet run
   ```

## Running

### Basic Usage

```bash
# Run with default settings (port 8765)
dotnet run

# Or run the compiled executable
dotnet AppConnectServer.dll
```

### Configuration

Set environment variables before running:

```bash
# Change port (default: 8765)
export APPCONNECT_PORT=8765

# Change device name (default: "My-PC")
export APPCONNECT_DEVICE_NAME="My-MacBook"

# Pre-shared key for testing (optional, base64-encoded 32-byte key)
export APPCONNECT_PRE_SHARED_KEY="base64encoded32bytekey=="
```

### macOS Example

```bash
# Set device name
export APPCONNECT_DEVICE_NAME="My-MacBook-Pro"

# Run server
dotnet run
```

## First Run

On first run, the server will:

1. Generate SSL certificate and RSA key pair
2. Create `certs/` directory with:
   - `server.crt` - SSL certificate
   - `server.key` - SSL private key
   - `rsa_private.pem` - RSA private key
   - `rsa_public.pem` - RSA public key
   - `qr_code.png` - QR code image
3. Display QR code in terminal
4. Start WebSocket server

## Connecting from Android App

1. Start the PC server
2. Scan the QR code displayed in the terminal (or open `certs/qr_code.png`)
3. Clipboard sync will work bidirectionally

## Troubleshooting

### Port Already in Use

```bash
# Change port
export APPCONNECT_PORT=8766
dotnet run
```

### Firewall Issues

**macOS:**
- System Preferences → Security & Privacy → Firewall
- Allow incoming connections for the app

**Linux:**
```bash
sudo ufw allow 8765/tcp
```

### Clipboard Not Working

**macOS:**
- Ensure `pbcopy` and `pbpaste` are available
- Check terminal permissions

**Linux:**
- Install `xclip`: `sudo apt-get install xclip`
- Ensure X11 is running

### Certificate Issues

If you get certificate errors:
- Delete the `certs/` directory and restart
- The server will regenerate certificates

## Development

### Project Structure

```
pc-server-dotnet/
├── Program.cs                 # Entry point
├── Server.cs                  # Main server class
├── Configuration.cs           # Configuration management
├── Core/
│   ├── EncryptionManager.cs   # AES-256-GCM encryption
│   └── KeyExchangeManager.cs # RSA key exchange
├── Network/
│   └── WebSocketServer.cs    # WebSocket server
├── Certificate/
│   └── CertificateManager.cs # SSL certificate management
├── Clipboard/
│   ├── ClipboardMonitor.cs    # Clipboard monitoring
│   └── ClipboardWriter.cs   # Clipboard writing
├── QR/
│   └── QRCodeGenerator.cs    # QR code generation
└── Discovery/
    └── MDnsService.cs        # mDNS service discovery
```

### Building for Release

```bash
# Build release version
dotnet build -c Release

# Publish self-contained (includes .NET runtime)
dotnet publish -c Release -r osx-x64 --self-contained true

# Output will be in: bin/Release/net8.0/osx-x64/publish/
```

## Protocol Compatibility

This implementation maintains 100% protocol compatibility with:
- Android AppConnect app
- Python PC server implementation

All encryption, message formats, and key exchange match exactly.

## Security

- Self-signed SSL certificate for WSS connection
- Certificate fingerprint pinning (prevents MITM attacks)
- RSA key exchange for AES encryption key
- All clipboard data encrypted with AES-256-GCM

## License

See LICENSE file in the root directory.
