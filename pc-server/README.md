# AppConnect PC Server

PC daemon/server for syncing clipboard with the AppConnect Android app via QR code pairing.

## Features

- QR code generation for easy pairing
- Secure WebSocket (WSS) server with certificate pinning
- mDNS service discovery
- AES-256-GCM encryption for clipboard data
- Cross-platform clipboard monitoring (Windows, Linux, macOS)

## Requirements

- Python 3.8+
- Network access on port 8765 (default)

## Installation

1. Install dependencies:
```bash
pip install -r requirements.txt
```

2. Run the server:
```bash
python -m src.server
```

The server will:
- Generate SSL certificate and RSA key pair (first run)
- Generate and save QR code image to `certs/qr_code.png`
- Display QR code in terminal
- Start WebSocket server on port 8765
- Broadcast mDNS service for discovery

## Configuration

Environment variables:
- `APPCONNECT_PORT`: WebSocket server port (default: 8765)
- `APPCONNECT_DEVICE_NAME`: Device name shown in QR code (default: "My-PC")

## Usage

1. Start the server
2. Open the QR code image file (`certs/qr_code.png`) or scan the QR code displayed in the terminal
3. Scan the QR code with the Android app
4. Clipboard sync will work bidirectionally

## Security

- Self-signed SSL certificate for WSS connection
- Certificate fingerprint pinning (prevents MITM attacks)
- RSA key exchange for AES encryption key
- All clipboard data encrypted with AES-256-GCM

## Troubleshooting

- **Port already in use**: Change port with `APPCONNECT_PORT` environment variable
- **Firewall blocking**: Allow incoming connections on configured port
- **QR code not scanning**: Ensure phone and PC are on same network

