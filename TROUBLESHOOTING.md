# Troubleshooting Guide

This guide helps resolve common issues with AppConnect.

## Table of Contents

- [Connection Issues](#connection-issues)
- [Pairing Problems](#pairing-problems)
- [Clipboard Sync Not Working](#clipboard-sync-not-working)
- [Background Service Issues](#background-service-issues)
- [Build and Installation](#build-and-installation)
- [Permissions](#permissions)

## Connection Issues

### WebSocket Connection Fails

**Symptoms**: App shows "Disconnected" status, cannot connect to PC

**Solutions**:

1. **Check PC companion app is running**
   - Verify the PC app is active and listening on port 8765
   - Check firewall isn't blocking the port

2. **Verify network connectivity**
   - Ensure both devices are on the same Wi-Fi network
   - Try pinging the PC from your Android device
   - Check if other apps can connect to PC

3. **Check mDNS service**
   - PC should broadcast `_appconnect._tcp` service
   - Use a network scanner to verify mDNS is visible
   - Some routers block mDNS - try direct IP connection

4. **Certificate issues**
   - Re-pair the devices by scanning QR code again
   - Ensure certificate hasn't expired
   - Check system clock is synchronized

### Bluetooth Fallback Not Working

**Symptoms**: WebSocket fails but Bluetooth doesn't activate

**Solutions**:

1. **Check Bluetooth permissions**
   - Android 12+: Enable BLUETOOTH_CONNECT permission
   - Settings → Apps → AppConnect → Permissions

2. **Verify Bluetooth is enabled**
   - Turn Bluetooth on in Android settings
   - Ensure PC Bluetooth is also enabled

3. **Check paired device has Bluetooth address**
   - Device must be paired via CDM first
   - Bluetooth address stored during pairing

## Pairing Problems

### QR Code Scanner Not Working

**Symptoms**: Camera doesn't open or QR code not recognized

**Solutions**:

1. **Camera permission**
   - Grant camera permission when prompted
   - Settings → Apps → AppConnect → Permissions → Camera

2. **QR code format**
   - Ensure PC app generates correct QR format
   - QR should contain: IP, port, public key, certificate fingerprint

3. **Lighting and focus**
   - Ensure good lighting conditions
   - Hold phone steady until code is recognized
   - Try moving closer or further from screen

### "Pairing Failed" Error

**Symptoms**: QR code scans but pairing doesn't complete

**Solutions**:

1. **Network accessibility**
   - Ensure PC is reachable at IP shown in QR code
   - Try manual ping test

2. **Port accessibility**
   - Check firewall allows incoming connections on specified port
   - Default port: 8765

3. **Companion Device Manager**
   - Android 12+ requires CDM association
   - Grant permission when prompted
   - Check Settings → Connected devices

## Clipboard Sync Not Working

### Clipboard Not Syncing to PC

**Symptoms**: Copy text on Android but PC doesn't receive it

**Solutions**:

1. **Check service is running**
   - Pull down notification shade
   - Look for "Connected to PC" notification
   - If not visible, restart sync from Quick Settings tile

2. **Verify clipboard access**
   - Android 13+: Foreground service has clipboard access
   - Background: Enable Accessibility Service
   - Or use ADB logcat method for development

3. **Check sync mode**
   - Manual: Use Quick Settings tile
   - ADB: Enable developer options and ADB
   - Accessibility: Enable in Android settings

### Clipboard Not Syncing from PC

**Symptoms**: Copy on PC but Android clipboard not updated

**Solutions**:

1. **Check app state**
   - Foreground: Direct clipboard write
   - Background: Check for notification with "Copy" button
   - Tap notification to write to clipboard

2. **Notification permissions**
   - Grant notification permission
   - Settings → Apps → AppConnect → Notifications

3. **Check encryption**
   - Both devices must use same encryption key
   - Re-pair if encryption errors appear in logs

## Background Service Issues

### Service Stops After Screen Lock

**Symptoms**: Sync works when app is open but stops when screen locks

**Solutions**:

1. **Battery optimization**
   - Disable battery optimization for AppConnect
   - Settings → Battery → Battery optimization → All apps → AppConnect → Don't optimize

2. **Companion Device Manager**
   - Ensure CDM association is active
   - This grants background execution permission

3. **Foreground service**
   - Verify "connectedDevice" notification is visible
   - Service should persist even when screen is locked

### "Service Stopped" Notification

**Symptoms**: Service unexpectedly stops

**Solutions**:

1. **Check connection**
   - Both transports failed (WebSocket and Bluetooth)
   - Check connection troubleshooting above

2. **System killed service**
   - Low memory condition may kill service
   - Service will restart when memory available
   - Use BootCompletedReceiver for auto-restart

3. **Check logs**
   - Use Android Studio Logcat
   - Filter by "AppConnect" tag
   - Look for error messages

## Build and Installation

### Build Fails

**Symptoms**: Gradle build errors

**Solutions**:

1. **Check JDK version**
   ```bash
   java -version  # Should be 21
   ```
   - Install JDK 21 if needed

2. **Gradle sync**
   ```bash
   ./gradlew clean
   ./gradlew build
   ```

3. **Clear caches**
   - Android Studio → File → Invalidate Caches / Restart

4. **Check dependency versions**
   - Review VERSIONS.md
   - Ensure all versions are compatible

### Installation Fails

**Symptoms**: APK won't install on device

**Solutions**:

1. **Minimum Android version**
   - Requires Android 10+ (API 29)
   - Check device Android version

2. **Storage space**
   - Ensure sufficient storage available
   - App size: ~15-20 MB

3. **Previous installation**
   - Uninstall old version first
   - Or use same signing key

## Permissions

### Required Permissions Not Granted

**Symptoms**: Features don't work, permission dialogs don't appear

**Solutions**:

1. **Camera** (for QR scanning)
   - Settings → Apps → AppConnect → Permissions → Camera

2. **Notifications**
   - Settings → Apps → AppConnect → Notifications

3. **Bluetooth** (Android 12+)
   - Settings → Apps → AppConnect → Permissions → Nearby devices

4. **Accessibility** (optional, for background clipboard)
   - Settings → Accessibility → AppConnect

5. **Companion Device Manager**
   - Automatically requested during pairing
   - Check Settings → Connected devices

## Advanced Debugging

### Enable Debug Logging

1. Use debug build variant
2. Check Logcat with filter: `tag:AppConnect`
3. Key log tags:
   - `ClipboardSyncService`
   - `SyncManager`
   - `WebSocketClient`
   - `EncryptionManager`

### Check Database

```bash
adb shell
run-as dev.appconnect
cd databases
sqlite3 app_database
.tables
SELECT * FROM clipboard_items;
SELECT * FROM paired_devices;
```

### Network Debugging

```bash
# Check if PC is reachable
adb shell ping <PC_IP>

# Check if port is open
adb shell nc -zv <PC_IP> 8765
```

## Getting Help

If you're still experiencing issues:

1. Check existing issues on GitHub
2. Create a new issue with:
   - Android version
   - Device model
   - Steps to reproduce
   - Relevant logs
   - Screenshots if applicable

## PC Companion App Issues

Since PC companion app is separate:

1. Verify PC app is running
2. Check PC firewall settings
3. Ensure mDNS service is broadcasting
4. Verify QR code generation
5. Check PC app logs

For PC app specific issues, refer to PC companion documentation.
