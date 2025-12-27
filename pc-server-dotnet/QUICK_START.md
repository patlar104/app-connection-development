# Quick Start Guide

Get AppConnect PC Server running on your MacBook in 5 minutes!

## Prerequisites Check

```bash
# Check if .NET is installed
dotnet --version
# If not installed, see MACOS_SETUP.md Step 1

# Check clipboard tools (should be pre-installed)
which pbcopy
which pbpaste
```

## Installation Steps

### 1. Install .NET 8.0 SDK

**Using Homebrew (easiest):**
```bash
brew install --cask dotnet-sdk
```

**Or download from:**
https://dotnet.microsoft.com/download/dotnet/8.0

### 2. Navigate to Project

```bash
cd pc-server-dotnet
```

### 3. Build and Run

```bash
# Restore packages
dotnet restore

# Build
dotnet build

# Run
dotnet run
```

### 4. Configure (Optional)

```bash
# Set your device name
export APPCONNECT_DEVICE_NAME="My-MacBook-Pro"

# Change port if needed
export APPCONNECT_PORT=8765

# Run again
dotnet run
```

## What You'll See

On first run:

1. ✅ Certificate generation
2. ✅ RSA key pair generation  
3. ✅ QR code displayed in terminal
4. ✅ Server starting on port 8765

## Connect from Android

1. Open AppConnect app
2. Scan the QR code from terminal
3. Wait for connection
4. Test clipboard sync!

## Troubleshooting

**Port in use?**
```bash
export APPCONNECT_PORT=8766
dotnet run
```

**Clipboard not working?**
- Check Terminal has Accessibility permissions
- System Preferences → Security & Privacy → Privacy → Accessibility

**Can't connect?**
- Ensure phone and Mac are on same Wi-Fi network
- Check firewall settings

## Next Steps

- See `MACOS_SETUP.md` for detailed setup
- See `README.md` for full documentation
- Check logs if issues occur

## That's It! 🎉

Your server is running and ready to sync clipboard with your Android device!
