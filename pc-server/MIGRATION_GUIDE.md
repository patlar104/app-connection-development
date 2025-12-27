# Migration Guide: Refactored Encryption Module

## Overview

The encryption module has been refactored for better architecture, security, and maintainability. The old `encryption.py` module is still available for backward compatibility, but the new modular structure provides:

- Better separation of concerns
- Improved error handling
- Key management features
- Extensibility for future enhancements

## New Structure

```
src/encryption/
├── __init__.py                    # Main exports
├── exceptions.py                  # Custom exceptions
├── core/
│   ├── encryption_manager.py     # Core AES-GCM encryption
│   └── key_manager.py            # Key lifecycle management
└── key_exchange/
    └── rsa_exchange.py           # RSA key exchange
```

## Backward Compatibility

The old `encryption.py` module still works. It's been updated to use the new module internally, so existing code continues to work without changes.

## Migration Steps

### Option 1: Keep Using Old API (No Changes Required)

```python
from src.encryption import EncryptionManager

# This still works exactly as before
encryption = EncryptionManager(key)
encrypted = encryption.encrypt_for_transmission("Hello")
decrypted = encryption.decrypt_from_transmission(encrypted)
```

### Option 2: Use New Modular API

#### Basic Usage (Same as Before)

```python
from src.encryption import EncryptionManager

encryption = EncryptionManager(key)
encrypted = encryption.encrypt_for_transmission("Hello")
decrypted = encryption.decrypt_from_transmission(encrypted)
```

#### Advanced Usage with Key Management

```python
from src.encryption import EncryptionManager, KeyManager, KeyDerivationMethod

# Create key manager with expiration
key_manager = KeyManager(
    key=my_key,
    expiration_seconds=3600,  # 1 hour
    on_expiration=lambda: print("Key expired!")
)

# Use with encryption manager
encryption = EncryptionManager(key_manager=key_manager)

# Encryption automatically uses key manager
encrypted = encryption.encrypt_for_transmission("Hello")
```

#### Using RSA Key Exchange

```python
from src.encryption import RSAKeyExchange
from pathlib import Path

# Initialize RSA key exchange
rsa_exchange = RSAKeyExchange(
    private_key_file=Path("certs/rsa_private.pem"),
    key_size=2048
)

# Get public key for QR code
public_key_b64 = rsa_exchange.get_public_key_base64()

# Decrypt AES key from client
encrypted_key_b64 = "..."
aes_key = rsa_exchange.decrypt_aes_key(encrypted_key_b64)
```

## Updating WebSocket Server

The `websocket_server.py` can be updated to use the new RSA key exchange:

```python
# Old way (still works):
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding

# New way (recommended):
from src.encryption import RSAKeyExchange

# In __init__:
self.rsa_exchange = RSAKeyExchange(rsa_private_key_file)

# In _handle_key_exchange:
aes_key = self.rsa_exchange.decrypt_aes_key(encrypted_key_b64)
```

## Benefits of New Architecture

1. **Better Error Handling**
   - Specific exception types (`EncryptionError`, `DecryptionError`, etc.)
   - More informative error messages
   - Easier debugging

2. **Key Management**
   - Key expiration
   - Key derivation support
   - Secure key clearing
   - Key rotation

3. **Extensibility**
   - Easy to add new key exchange methods (ECDH, etc.)
   - Easy to add new encryption algorithms
   - Plugin architecture for key storage

4. **Security Improvements**
   - Better input validation
   - Constant-time operations where possible
   - Secure key handling

## Testing

All existing tests should continue to work. The new module maintains 100% backward compatibility with the old API.

## Future Enhancements

The new architecture enables:
- ECDH key exchange (more efficient than RSA)
- Key derivation (PBKDF2, Argon2)
- Secure key storage (DPAPI on Windows, Keychain on macOS)
- Key rotation
- Forward secrecy
