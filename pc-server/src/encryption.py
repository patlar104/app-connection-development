"""AES-256-GCM encryption/decryption matching Android implementation."""
import base64
import json
import hashlib
from typing import Optional
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend
import secrets


class EncryptionManager:
    """Manages AES-256-GCM encryption matching Android EncryptionManager."""
    
    def __init__(self, key: Optional[bytes] = None):
        """
        Initialize encryption manager.
        
        Args:
            key: AES-256 key (32 bytes). If None, key must be set via set_key().
        """
        self._key: Optional[bytes] = key
        self._aesgcm: Optional[AESGCM] = None
        if key:
            self._aesgcm = AESGCM(key)
            
    def set_key(self, key: bytes) -> None:
        """Set the AES encryption key."""
        if len(key) != 32:
            raise ValueError("AES key must be 32 bytes (256 bits)")
        self._key = key
        self._aesgcm = AESGCM(key)
        
    def generate_key(self) -> bytes:
        """Generate a random AES-256 key."""
        key = secrets.token_bytes(32)
        self.set_key(key)
        return key
        
    def encrypt(self, data: str) -> tuple[bytes, bytes]:
        """
        Encrypt data using AES-256-GCM.
        
        Args:
            data: Plaintext string to encrypt
            
        Returns:
            Tuple of (iv, encrypted_bytes) where:
            - iv: 12-byte initialization vector
            - encrypted_bytes: Encrypted data with 16-byte GCM tag appended
        """
        if not self._aesgcm:
            raise ValueError("Encryption key not set")
            
        # Generate random 12-byte IV
        iv = secrets.token_bytes(12)
        
        # Encrypt data (GCM automatically appends 16-byte tag)
        encrypted = self._aesgcm.encrypt(iv, data.encode('utf-8'), None)
        
        return iv, encrypted
        
    def decrypt(self, iv: bytes, encrypted: bytes) -> str:
        """
        Decrypt data using AES-256-GCM.
        
        Args:
            iv: 12-byte initialization vector
            encrypted: Encrypted data with 16-byte GCM tag appended
            
        Returns:
            Decrypted plaintext string
        """
        if not self._aesgcm:
            raise ValueError("Encryption key not set")
            
        # Decrypt (GCM automatically verifies 16-byte tag)
        decrypted = self._aesgcm.decrypt(iv, encrypted, None)
        
        return decrypted.decode('utf-8')
        
    def encrypt_for_transmission(self, data: str) -> str:
        """
        Encrypt and format for transmission: {ivBase64}|{encryptedBase64}
        
        Uses NO_WRAP base64 encoding (no padding, no line breaks) to match Android.
        """
        iv, encrypted = self.encrypt(data)
        
        # Base64 encode with NO_WRAP (no padding, no line breaks)
        iv_b64 = base64.b64encode(iv).decode('ascii').rstrip('=')
        encrypted_b64 = base64.b64encode(encrypted).decode('ascii').rstrip('=')
        
        return f"{iv_b64}|{encrypted_b64}"
        
    def decrypt_from_transmission(self, message: str) -> str:
        """
        Decrypt message in format: {ivBase64}|{encryptedBase64}
        
        Handles NO_WRAP base64 encoding from Android (no padding, no line breaks).
        Adds padding back if needed since Python's base64.decode() may require it.
        """
        parts = message.split("|")
        if len(parts) != 2:
            raise ValueError("Invalid message format: expected {ivBase64}|{encryptedBase64}")
            
        iv_b64, encrypted_b64 = parts
        
        # Base64 decode - add padding if needed (padding length = (4 - len % 4) % 4)
        # This handles NO_WRAP format from Android which strips padding
        def add_padding(s: str) -> str:
            missing_padding = len(s) % 4
            if missing_padding:
                return s + '=' * (4 - missing_padding)
            return s
        
        iv = base64.b64decode(add_padding(iv_b64))
        encrypted = base64.b64decode(add_padding(encrypted_b64))
        
        return self.decrypt(iv, encrypted)
        
    @staticmethod
    def calculate_hash(content: str) -> str:
        """
        Calculate SHA-256 hash of content in lowercase hex format.
        
        Matches Android ClipboardSyncService.calculateHash() which uses %02x (lowercase).
        """
        digest = hashlib.sha256(content.encode('utf-8')).digest()
        return digest.hex()  # lowercase hex

