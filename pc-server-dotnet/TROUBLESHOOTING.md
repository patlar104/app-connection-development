# Troubleshooting Guide

Common errors and their solutions when running AppConnect PC Server.

## Build Errors

### Error: "The type or namespace name 'X' could not be found"

**Solution:**
```bash
# Clean and restore packages
dotnet clean
dotnet restore
dotnet build
```

### Error: "Package 'X' not found"

**Solution:**
```bash
# Clear NuGet cache and restore
dotnet nuget locals all --clear
dotnet restore
```

### Error: "CS0246: The type or namespace name 'QRCodeGenerator' could not be found"

**Solution:**
- This is a namespace conflict. The code uses `QRCoder.QRCodeGenerator` to avoid conflicts.
- Ensure you have the latest version of the code.

## Runtime Errors

### Error: "Access to the path '/path/to/certs' is denied"

**Solution:**
```bash
# Create certs directory manually
mkdir -p certs
chmod 755 certs
```

### Error: "Address already in use" or "Port 8765 is already in use"

**Solution:**
```bash
# Use a different port
export APPCONNECT_PORT=8766
dotnet run

# Or find and kill the process using the port
lsof -ti:8765 | xargs kill -9
```

### Error: "HttpListener requires elevated permissions"

**Solution (macOS/Linux):**
- HttpListener on non-Windows platforms may require special permissions
- Try running with `sudo` (not recommended for production)
- Or use a different port (>1024)

**Better Solution:**
- Use Kestrel/ASP.NET Core instead of HttpListener (future enhancement)

### Error: "Could not determine local IP address"

**Solution:**
```bash
# Check your network connection
ifconfig | grep "inet "

# Ensure you're connected to Wi-Fi or Ethernet
# If using VPN, try disconnecting temporarily
```

### Error: "pbcopy: command not found" or clipboard not working

**Solution (macOS):**
```bash
# Verify pbcopy exists
which pbcopy
# Should show: /usr/bin/pbcopy

# If missing, it's a system issue - contact Apple support
# More likely: Terminal needs Accessibility permissions
```

**Grant Terminal Accessibility Permissions:**
1. System Preferences → Security & Privacy → Privacy
2. Click "Accessibility"
3. Click the lock icon and enter your password
4. Check the box next to Terminal (or your terminal app)
5. Restart the server

### Error: "Certificate generation failed"

**Solution:**
```bash
# Delete existing certificates and regenerate
rm -rf certs/
dotnet run
```

### Error: "RSA key generation failed"

**Solution:**
```bash
# Delete existing RSA keys
rm -f certs/rsa_private.pem certs/rsa_public.pem
dotnet run
```

## Connection Errors

### Android app can't connect

**Checklist:**
1. ✅ Server is running (`dotnet run`)
2. ✅ Phone and Mac are on the same Wi-Fi network
3. ✅ Firewall is not blocking port 8765
4. ✅ IP address in QR code is correct
5. ✅ Port is accessible

**Test connectivity:**
```bash
# On your Mac, check if server is listening
lsof -i :8765

# From your phone, try to connect to the IP shown in QR code
# You can test with a simple HTTP request
```

### QR code not scanning

**Solutions:**
1. Open the saved QR code image: `certs/qr_code.png`
2. Ensure QR code is clearly visible
3. Try increasing terminal font size
4. Check that connection info JSON is valid

### "Key exchange failed" error

**Solutions:**
1. Ensure RSA keys are generated correctly
2. Delete and regenerate keys:
   ```bash
   rm -f certs/rsa_private.pem certs/rsa_public.pem
   dotnet run
   ```
3. Check that Android app supports RSA key exchange
4. For testing, use pre-shared key:
   ```bash
   # Generate a 32-byte key and base64 encode it
   openssl rand -base64 32
   export APPCONNECT_PRE_SHARED_KEY="<base64-key>"
   dotnet run
   ```

## Performance Issues

### High CPU usage

**Solution:**
- Clipboard monitoring checks every 100ms by default
- This is normal for responsive clipboard sync
- If too high, you can adjust the debounce time in code

### Memory leaks

**Solution:**
- Ensure WebSocket connections are properly closed
- Check for proper disposal of RSA keys
- Restart server periodically if needed

## Platform-Specific Issues

### macOS: "Operation not permitted"

**Solution:**
- Grant Terminal Accessibility permissions (see above)
- Check System Preferences → Security & Privacy

### Linux: "xclip: command not found"

**Solution:**
```bash
# Install xclip
sudo apt-get install xclip

# Or use xsel
sudo apt-get install xsel
```

### Linux: "No X11 display"

**Solution:**
- Ensure X11 is running
- If using SSH, use X11 forwarding:
  ```bash
  ssh -X user@host
  ```

## Getting Help

If you encounter an error not listed here:

1. **Check the logs:**
   - Server logs are displayed in the terminal
   - Look for error messages with stack traces

2. **Verify .NET version:**
   ```bash
   dotnet --version
   # Should be 8.0.x or higher
   ```

3. **Check system requirements:**
   - .NET 8.0 SDK installed
   - Clipboard tools available (pbcopy/pbpaste on macOS)
   - Network connectivity

4. **Try clean build:**
   ```bash
   dotnet clean
   rm -rf bin/ obj/
   dotnet restore
   dotnet build
   dotnet run
   ```

5. **Check for known issues:**
   - See IMPLEMENTATION_NOTES.md for known limitations
   - Check if your issue is a known limitation

## Common Error Codes

- **Exit code 1**: General error - check logs
- **Exit code 139**: Segmentation fault - usually .NET runtime issue
- **Exit code 255**: Fatal error - check .NET installation

## Still Having Issues?

1. Ensure you're using the latest code
2. Check that all prerequisites are installed
3. Try running with verbose logging (if implemented)
4. Check system logs for additional information
