# AppConnect

A personal-use Android app for syncing clipboard between Android device and PC. Implements a Samsung Phone Link / KDE Connect-style experience using proven open-source patterns.

## Features

- **Clipboard Synchronization**: Seamlessly sync clipboard content between Android and PC
- **Secure Pairing**: QR code-based pairing with certificate pinning for MITM prevention
- **Multiple Transport Methods**: WebSocket (primary) and Bluetooth (fallback)
- **Background Sync**: Uses Companion Device Manager for legitimate background operations
- **Privacy-First**: End-to-end encryption, sensitive data handling, and user-controlled sync
- **Robust Architecture**: MVVM with Clean Architecture, Room persistence, and health monitoring

## Requirements

- Android 10+ (API 29+)
- Android Studio with AGP 8.13.2+
- JDK 21
- Physical device for CDM testing (Android 12+)

## Architecture

### Components

- **Companion Device Manager (CDM)**: Grants background permissions for legitimate device pairing
- **Foreground Service**: `connectedDevice` type for persistent connection
- **Room Database**: Local persistence with TTL-based cleanup
- **Encryption**: AES-256-GCM with Android Keystore
- **Network Layer**: WebSocket (TLS) and Bluetooth (BLE/Classic) support

### Sync Modes

1. **Manual Sync**: Quick Settings tile trigger
2. **ADB-Enabled Automatic**: Logcat monitoring (developer setup)
3. **Accessibility Service**: Full background clipboard access (user permission required)

## Building

```bash
./gradlew assembleDebug
```

## Setup

1. Build and install the app on your Android device
2. Pair with PC using QR code scanner
3. Grant necessary permissions (Notifications, Accessibility if desired)
4. Start clipboard sync service

## PC Companion

A separate PC daemon application is required to receive clipboard data. The PC app must:

- Broadcast mDNS service: `_appconnect._tcp` with TXT record `app_id=dev.appconnect`
- Generate QR codes with connection info (IP, port, public key, certificate fingerprint)
- Listen for WebSocket connections on port 8765 (configurable)
- Support Bluetooth fallback (optional)

## Security

- **Certificate Pinning**: QR code contains certificate fingerprint for SSL validation
- **End-to-End Encryption**: AES-256-GCM encryption for all clipboard data
- **No Cloud Storage**: All data remains local, no external servers
- **Trust-on-First-Use**: Physical QR code scan prevents MITM attacks

## License

See LICENSE file for details.

## Contributing

This is a personal-use project. Contributions are welcome but please note this is designed for individual use cases.

