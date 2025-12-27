# C# Error Code Explanation

## "@anysphere.csharp.C# (9-10)" Error

This error code is from the C# language server (OmniSharp/Roslyn) in your IDE. The format suggests:
- **@anysphere.csharp.C#**: C# language server diagnostic
- **(9-10)**: Likely refers to lines 9-10 in a file, or error codes 9-10

### Lines 9-10 in WebSocketServer.cs

```csharp
using AppConnectServer.Core;
using AppConnectServer.Clipboard;
```

## Possible Causes

### 1. **Namespace Not Found** (Most Common)
The language server can't find the namespaces `AppConnectServer.Core` or `AppConnectServer.Clipboard`.

**Solution:**
```bash
# Restore NuGet packages and rebuild
cd pc-server-dotnet
dotnet clean
dotnet restore
dotnet build
```

### 2. **Missing Project References**
The project file might be missing references.

**Check:** Ensure `AppConnectServer.csproj` includes all necessary files.

### 3. **Language Server Indexing Issue**
The IDE's language server hasn't finished indexing the project.

**Solution:**
- Restart your IDE/editor
- Reload the window (VS Code: Cmd+Shift+P → "Reload Window")
- Wait for indexing to complete (check status bar)

### 4. **Circular Dependency**
There might be a circular reference between namespaces.

**Check:** The current structure should be fine, but verify:
- `Core` doesn't reference `Network`
- `Network` references `Core` and `Clipboard` (this is OK)

### 5. **Missing Using Statements**
Some types might need additional using statements.

**Current using statements in WebSocketServer.cs:**
```csharp
using System.Net;
using System.Net.Security;
using System.Net.WebSockets;
using System.Security.Cryptography.X509Certificates;
using System.Text;
using System.Text.Json;
using System.Threading;
using Microsoft.Extensions.Logging;
using AppConnectServer.Core;        // Line 9
using AppConnectServer.Clipboard;   // Line 10
```

## How to Diagnose

### Step 1: Check Actual Error Message
Look at the full error message in your IDE. It should tell you:
- What's missing
- Which type can't be found
- What the actual problem is

### Step 2: Try Building
```bash
cd pc-server-dotnet
dotnet build
```

If the build succeeds, it's likely a language server issue, not a real compilation error.

### Step 3: Check for Red Underlines
In your IDE, check if there are red underlines on:
- `EncryptionManager`
- `KeyExchangeManager`
- `ClipboardWriter`
- `ClipboardMonitor`

### Step 4: Verify Files Exist
Ensure these files exist:
- `Core/EncryptionManager.cs`
- `Core/KeyExchangeManager.cs`
- `Clipboard/ClipboardWriter.cs`
- `Clipboard/ClipboardMonitor.cs`

## Common Solutions

### Solution 1: Restore and Rebuild
```bash
cd pc-server-dotnet
dotnet clean
dotnet restore
dotnet build
```

### Solution 2: Restart Language Server
**VS Code:**
1. Cmd+Shift+P (Mac) or Ctrl+Shift+P (Windows/Linux)
2. Type "OmniSharp: Restart OmniSharp"
3. Press Enter

**Other IDEs:**
- Restart the IDE
- Reload the project

### Solution 3: Check Project File
Ensure `AppConnectServer.csproj` is correct and all files are included.

### Solution 4: Verify Namespaces
Check that all files have correct namespace declarations:

**Core/EncryptionManager.cs:**
```csharp
namespace AppConnectServer.Core;
```

**Clipboard/ClipboardWriter.cs:**
```csharp
namespace AppConnectServer.Clipboard;
```

## If Error Persists

### Check the Full Error
The error message should provide more details. Common messages:
- "The type or namespace name 'X' could not be found"
- "CS0246: The type or namespace name 'X' could not be found"
- "CS0234: The type or namespace name 'X' does not exist"

### Verify Compilation
If `dotnet build` succeeds but the IDE shows errors, it's a language server issue:
1. The code is correct
2. The IDE needs to refresh
3. Try restarting the language server

### Check for Missing Dependencies
Ensure all NuGet packages are restored:
```bash
dotnet restore
```

## Summary

The error "@anysphere.csharp.C# (9-10)" is likely:
1. **Language server indexing issue** (most common) - Restart language server
2. **Missing project references** - Run `dotnet restore`
3. **IDE cache issue** - Restart IDE or reload window

**Quick Fix:**
```bash
cd pc-server-dotnet
dotnet clean
dotnet restore
dotnet build
```

If `dotnet build` succeeds, the code is correct and it's just an IDE/language server issue.
