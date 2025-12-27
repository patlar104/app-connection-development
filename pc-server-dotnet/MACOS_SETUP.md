# macOS Setup Guide

Complete guide for setting up and running AppConnect PC Server on macOS.

## Step 1: Install .NET 8.0 SDK

### Option A: Using Homebrew (Recommended)

1. **Install Homebrew** (if not already installed)
   ```bash
   /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
   ```

2. **Install .NET SDK**
   ```bash
   brew install --cask dotnet-sdk
   ```

3. **Verify Installation**
   ```bash
   dotnet --version
   ```
   Should output: `8.0.x` or higher

### Option B: Direct Download

1. **Download .NET 8.0 SDK**
   - Visit: https://dotnet.microsoft.com/download/dotnet/8.0
   - Download the macOS installer (.pkg file)
   - Run the installer and follow the prompts

2. **Verify Installation**
   ```bash
   dotnet --version
   ```

## Step 2: Verify System Tools

macOS comes with clipboard tools pre-installed, but let's verify:

```bash
# Check pbcopy (copy to clipboard)
which pbcopy
# Should show: /usr/bin/pbcopy

# Check pbpaste (paste from clipboard)
which pbpaste
# Should show: /usr/bin/pbpaste

# Test clipboard
echo "test" | pbcopy
pbpaste
# Should output: test
```

If these commands work, you're all set!

## Step 3: Clone/Navigate to Project

```bash
# If you have the project in a git repository
cd /path/to/workspace/pc-server-dotnet

# Or navigate to the project directory
cd pc-server-dotnet
```

## Step 4: Build the Project

```bash
# Restore NuGet packages
dotnet restore

# Build the project
dotnet build
```

Expected output:
```
Build succeeded.
    0 Warning(s)
    0 Error(s)
```

## Step 5: Configure (Optional)

Set environment variables if you want to customize:

```bash
# Set device name (optional)
export APPCONNECT_DEVICE_NAME="My-MacBook-Pro"

# Set port (optional, default is 8765)
export APPCONNECT_PORT=8765
```

## Step 6: Run the Server

```bash
# Run the server
dotnet run
```

### First Run

On first run, you'll see:

1. **Certificate Generation**
   ```
   Generating SSL certificate...
   Generated SSL certificate: certs/server.crt
   ```

2. **RSA Key Generation**
   ```
   Generating RSA key pair...
   RSA key pair generated and saved
   ```

3. **QR Code Display**
   ```
   === AppConnect QR Code ===
   [ASCII QR Code displayed here]
   === Connection Info ===
   {"n":"My-MacBook-Pro","ip":"192.168.1.100","p":8765,"k":"...","fp":"SHA256:..."}
   ======================
   ```

4. **Server Starting**
   ```
   Starting server services...
   Clipboard monitoring started
   Starting WebSocket server on 0.0.0.0:8765
   WebSocket server running on http://0.0.0.0:8765
   ```

## Step 7: Connect from Android App

1. **Open AppConnect app** on your Android device
2. **Scan the QR code** displayed in the terminal
   - Or open the image: `certs/qr_code.png`
3. **Wait for connection** - you should see connection logs
4. **Test clipboard sync** - copy text on either device

## Troubleshooting

### Issue: "Port 8765 is already in use"

**Solution:**
```bash
# Use a different port
export APPCONNECT_PORT=8766
dotnet run
```

### Issue: "Permission denied" when accessing clipboard

**Solution:**
- macOS may require Terminal to have accessibility permissions
- Go to: System Preferences → Security & Privacy → Privacy → Accessibility
- Add Terminal (or your terminal app) to the list
- Restart the server

### Issue: "Could not determine local IP address"

**Solution:**
- Ensure you're connected to a network (Wi-Fi or Ethernet)
- Check your network connection:
  ```bash
  ifconfig | grep "inet "
  ```
- If using VPN, try disconnecting temporarily

### Issue: Firewall blocking connections

**Solution:**
1. Go to: System Preferences → Security & Privacy → Firewall
2. Click "Firewall Options"
3. Add the `dotnet` process or allow incoming connections
4. Or temporarily disable firewall for testing

### Issue: QR code not scanning

**Solution:**
- Ensure phone and Mac are on the same network
- Check that the IP address in QR code is accessible from your phone
- Try opening `certs/qr_code.png` and scanning from there
- Verify port is not blocked by firewall

### Issue: Build errors

**Solution:**
```bash
# Clean and rebuild
dotnet clean
dotnet restore
dotnet build
```

### Issue: Missing dependencies

**Solution:**
```bash
# Restore packages
dotnet restore

# If that doesn't work, clear NuGet cache
dotnet nuget locals all --clear
dotnet restore
```

## Running as Background Service (Optional)

### Using launchd (macOS Service)

1. **Create plist file** (`~/Library/LaunchAgents/com.appconnect.server.plist`):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>com.appconnect.server</string>
    <key>ProgramArguments</key>
    <array>
        <string>/usr/local/bin/dotnet</string>
        <string>/path/to/pc-server-dotnet/AppConnectServer.dll</string>
    </array>
    <key>WorkingDirectory</key>
    <string>/path/to/pc-server-dotnet</string>
    <key>RunAtLoad</key>
    <true/>
    <key>KeepAlive</key>
    <true/>
    <key>StandardOutPath</key>
    <string>/tmp/appconnect-server.log</string>
    <key>StandardErrorPath</key>
    <string>/tmp/appconnect-server.error.log</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>APPCONNECT_DEVICE_NAME</key>
        <string>My-MacBook-Pro</string>
        <key>APPCONNECT_PORT</key>
        <string>8765</string>
    </dict>
</dict>
</plist>
```

2. **Load the service:**
```bash
launchctl load ~/Library/LaunchAgents/com.appconnect.server.plist
```

3. **Check status:**
```bash
launchctl list | grep appconnect
```

4. **View logs:**
```bash
tail -f /tmp/appconnect-server.log
```

## Quick Start Script

Create a script to make running easier:

```bash
#!/bin/bash
# save as: run-appconnect.sh

cd "$(dirname "$0")"
export APPCONNECT_DEVICE_NAME="My-MacBook-Pro"
dotnet run
```

Make it executable:
```bash
chmod +x run-appconnect.sh
./run-appconnect.sh
```

## Next Steps

- ✅ Server is running
- ✅ QR code is displayed
- ✅ Android app can connect
- ✅ Clipboard sync is working

Enjoy using AppConnect! 🎉
