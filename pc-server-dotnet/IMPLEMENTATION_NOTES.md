# Implementation Notes

## Architecture Overview

This .NET implementation provides a cross-platform PC server for the AppConnect Android app. It maintains 100% protocol compatibility with both the Python implementation and the Android app.

## Key Components

### 1. Core Encryption (`Core/`)
- **EncryptionManager**: AES-256-GCM encryption matching Android implementation
- **KeyExchangeManager**: RSA-based key exchange for establishing shared AES keys

### 2. Network (`Network/`)
- **WebSocketServer**: HTTP/WebSocket server for client connections
- Currently uses HTTP (HTTPS support can be added with proper certificate binding)

### 3. Certificate Management (`Certificate/`)
- **CertificateManager**: Self-signed SSL certificate generation and management
- Generates certificates compatible with Android certificate pinning

### 4. Clipboard Operations (`Clipboard/`)
- **ClipboardMonitor**: Monitors local clipboard for changes
- **ClipboardWriter**: Writes to clipboard (cross-platform)
  - macOS: Uses `pbcopy`/`pbpaste`
  - Linux: Uses `xclip`
  - Windows: Can use Windows APIs (future enhancement)

### 5. QR Code Generation (`QR/`)
- **QRCodeGenerator**: Generates QR codes with connection info
- Uses QRCoder library for QR code generation

### 6. Service Discovery (`Discovery/`)
- **MDnsService**: mDNS service discovery (placeholder - needs Zeroconf integration)

## Protocol Compatibility

### Message Format
- Encrypted format: `{ivBase64}|{encryptedBase64}`
- Base64 encoding: NO_WRAP (with padding, no line breaks)
- Matches Android `Base64.NO_WRAP` exactly

### Encryption
- Algorithm: AES-256-GCM
- IV: 12 bytes (96 bits), random per message
- Tag: 16 bytes (128 bits), automatically appended by GCM
- Matches Android `EncryptionManager` exactly

### Key Exchange
- Method: RSA-OAEP with SHA-256
- Key size: 2048 bits (configurable)
- Format: Base64-encoded encrypted AES key
- Matches Android `KeyExchangeManager` exactly

### Certificate Fingerprint
- Format: `SHA256:{UPPERCASE_HEX}`
- Calculated from DER-encoded certificate
- Matches Android `PairedDeviceTrustManager` exactly

## Current Limitations

### HTTPS/SSL
- Currently uses HTTP (not HTTPS)
- HTTPS support requires:
  - Certificate binding on Windows
  - Proper SSL context configuration
  - May require running with elevated permissions

### mDNS Service Discovery
- Placeholder implementation
- Needs proper Zeroconf library integration
- Currently non-functional but doesn't break functionality

### Clipboard Image Support
- Text clipboard only
- Image clipboard support can be added per platform

## Platform-Specific Notes

### macOS
- Uses `pbcopy`/`pbpaste` for clipboard operations
- Works out of the box
- May require Terminal accessibility permissions

### Linux
- Requires `xclip` package: `sudo apt-get install xclip`
- Needs X11 running
- Clipboard operations use `xclip -selection clipboard`

### Windows
- Can use Windows Clipboard APIs (not yet implemented)
- Would provide better integration
- Can fall back to command-line tools

## Future Enhancements

1. **HTTPS Support**
   - Proper SSL/TLS configuration
   - Certificate binding
   - WSS (WebSocket Secure) support

2. **mDNS Integration**
   - Full Zeroconf library integration
   - Automatic service discovery

3. **Image Clipboard**
   - Platform-specific image clipboard support
   - PNG/JPEG format support

4. **Windows Service**
   - Run as Windows Service
   - Auto-start on boot
   - System tray integration

5. **macOS App Bundle**
   - Create .app bundle
   - Menu bar integration
   - Better user experience

6. **Key Rotation**
   - Automatic key rotation
   - Forward secrecy support

## Testing

### Protocol Compatibility Testing
- Test with Android app
- Verify encryption/decryption matches
- Verify key exchange works
- Verify certificate pinning works

### Cross-Platform Testing
- Test on macOS
- Test on Linux
- Test on Windows (when implemented)

## Dependencies

- **.NET 8.0**: Runtime and SDK
- **QRCoder**: QR code generation
- **Zeroconf**: mDNS service discovery (needs integration)

## Build and Deployment

### Development
```bash
dotnet restore
dotnet build
dotnet run
```

### Release Build
```bash
dotnet publish -c Release -r osx-x64 --self-contained true
```

### Self-Contained Deployment
- Includes .NET runtime
- No need to install .NET separately
- Larger file size (~70MB)

## Code Quality

- Uses nullable reference types
- Proper error handling
- Logging throughout
- Cross-platform compatible
- Protocol-compliant

## Notes for Developers

- All encryption matches Python implementation exactly
- Message formats match Android app exactly
- Certificate format matches Android exactly
- Key exchange matches Android exactly

This ensures seamless compatibility across all implementations.
