"""AES-256-GCM encryption/decryption with improved error handling."""
import base64
import hashlib
import secrets
from typing import Optional

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.backends import default_backend

from ..exceptions import (
    EncryptionError,
    DecryptionError,
    InvalidMessageFormatError,
    KeyManagementError
)
from .key_manager import KeyManager


class EncryptionManager:
    """
    Manages AES-256-GCM encryption matching Android EncryptionManager.
    
    Improvements over original:
    - Better error handling with specific exceptions
    - Key management integration
    - Input validation
    - Constant-time operations where possible
    - Better message format validation
    """
    
    # Constants matching Android implementation
    AES_KEY_SIZE = 32  # 256 bits
    AES_IV_SIZE = 12   # 96 bits
    AES_TAG_SIZE = 16  # 128 bits
    
    def __init__(self, key: Optional[bytes] = None, key_manager: Optional[KeyManager] = None):
        """
        Initialize encryption manager.
        
        Args:
            key: AES-256 key (32 bytes). If None, key must be set via set_key().
            key_manager: Optional KeyManager instance for advanced key management.
                         If provided, key parameter is ignored.
        """
        self._key_manager: Optional[KeyManager] = key_manager
        self._aesgcm: Optional[AESGCM] = None
        
        if key_manager:
            # Use key manager
            self._key_manager = key_manager
            self._update_aesgcm()
        elif key:
            # Use direct key (backward compatibility)
            self.set_key(key)
    
    def set_key(self, key: bytes) -> None:
        """
        Set the AES encryption key.
        
        Args:
            key: AES-256 key (32 bytes)
            
        Raises:
            ValueError: If key is invalid size
        """
        if len(key) != self.AES_KEY_SIZE:
            raise ValueError(
                f"AES key must be {self.AES_KEY_SIZE} bytes (256 bits), "
                f"got {len(key)} bytes"
            )
        
        # If using key manager, update it
        if self._key_manager:
            self._key_manager.set_key(key)
        else:
            # Direct key storage (backward compatibility)
            self._aesgcm = AESGCM(key)
    
    def get_key(self) -> Optional[bytes]:
        """Get the current encryption key (if available)."""
        if self._key_manager:
            try:
                return self._key_manager.get_key()
            except KeyManagementError:
                return None
        return None
    
    def _update_aesgcm(self) -> None:
        """Update AESGCM instance from key manager."""
        if self._key_manager:
            try:
                key = self._key_manager.get_key()
                self._aesgcm = AESGCM(key)
            except KeyManagementError as e:
                self._aesgcm = None
                raise EncryptionError(f"Key manager error: {e}") from e
    
    def generate_key(self) -> bytes:
        """
        Generate a random AES-256 key.
        
        Returns:
            New 32-byte encryption key
        """
        if self._key_manager:
            return self._key_manager.generate_key()
        else:
            key = secrets.token_bytes(self.AES_KEY_SIZE)
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
            
        Raises:
            EncryptionError: If encryption fails
        """
        if not self._aesgcm:
            # Try to update from key manager
            if self._key_manager:
                self._update_aesgcm()
            
            if not self._aesgcm:
                raise EncryptionError("Encryption key not set")
        
        if not isinstance(data, str):
            raise ValueError(f"Data must be string, got {type(data).__name__}")
        
        try:
            # Generate random 12-byte IV
            iv = secrets.token_bytes(self.AES_IV_SIZE)
            
            # Encrypt data (GCM automatically appends 16-byte tag)
            encrypted = self._aesgcm.encrypt(
                iv,
                data.encode('utf-8'),
                None  # No additional authenticated data
            )
            
            return iv, encrypted
            
        except Exception as e:
            raise EncryptionError(f"Encryption failed: {e}") from e
    
    def decrypt(self, iv: bytes, encrypted: bytes) -> str:
        """
        Decrypt data using AES-256-GCM.
        
        Args:
            iv: 12-byte initialization vector
            encrypted: Encrypted data with 16-byte GCM tag appended
            
        Returns:
            Decrypted plaintext string
            
        Raises:
            DecryptionError: If decryption fails
        """
        if not self._aesgcm:
            # Try to update from key manager
            if self._key_manager:
                self._update_aesgcm()
            
            if not self._aesgcm:
                raise DecryptionError("Encryption key not set")
        
        # Validate input sizes
        if len(iv) != self.AES_IV_SIZE:
            raise DecryptionError(
                f"IV must be {self.AES_IV_SIZE} bytes, got {len(iv)} bytes"
            )
        
        if len(encrypted) < self.AES_TAG_SIZE:
            raise DecryptionError(
                f"Encrypted data too short (must include {self.AES_TAG_SIZE}-byte tag)"
            )
        
        try:
            # Decrypt (GCM automatically verifies 16-byte tag)
            decrypted = self._aesgcm.decrypt(
                iv,
                encrypted,
                None  # No additional authenticated data
            )
            
            return decrypted.decode('utf-8')
            
        except Exception as e:
            raise DecryptionError(f"Decryption failed: {e}") from e
    
    def encrypt_for_transmission(self, data: str) -> str:
        """
        Encrypt and format for transmission: {ivBase64}|{encryptedBase64}
        
        Uses NO_WRAP base64 encoding (WITH padding, no line breaks) to match Android.
        Android's Base64.NO_WRAP includes padding - it only removes line breaks.
        
        Args:
            data: Plaintext string to encrypt
            
        Returns:
            Formatted encrypted message: "{ivBase64}|{encryptedBase64}"
            
        Raises:
            EncryptionError: If encryption fails
        """
        try:
            iv, encrypted = self.encrypt(data)
            
            # Base64 encode with NO_WRAP (WITH padding, no line breaks)
            # Android's Base64.NO_WRAP includes padding, so we must too
            iv_b64 = base64.b64encode(iv).decode('ascii')
            encrypted_b64 = base64.b64encode(encrypted).decode('ascii')
            
            return f"{iv_b64}|{encrypted_b64}"
            
        except EncryptionError:
            raise
        except Exception as e:
            raise EncryptionError(f"Failed to encrypt for transmission: {e}") from e
    
    def decrypt_from_transmission(self, message: str) -> str:
        """
        Decrypt message in format: {ivBase64}|{encryptedBase64}
        
        Handles NO_WRAP base64 encoding from Android (no padding, no line breaks).
        Adds padding back if needed since Python's base64.decode() may require it.
        
        Args:
            message: Encrypted message in format "{ivBase64}|{encryptedBase64}"
            
        Returns:
            Decrypted plaintext string
            
        Raises:
            InvalidMessageFormatError: If message format is invalid
            DecryptionError: If decryption fails
        """
        if not isinstance(message, str):
            raise InvalidMessageFormatError(
                f"Message must be string, got {type(message).__name__}"
            )
        
        # Validate message contains pipe separator
        if "|" not in message:
            raise InvalidMessageFormatError(
                "Invalid message format: expected {ivBase64}|{encryptedBase64}, "
                "missing pipe separator"
            )
        
        parts = message.split("|", 1)  # Split only on first pipe
        if len(parts) != 2:
            raise InvalidMessageFormatError(
                "Invalid message format: expected exactly one pipe separator"
            )
        
        iv_b64, encrypted_b64 = parts
        
        # Validate base64 strings are not empty
        if not iv_b64 or not encrypted_b64:
            raise InvalidMessageFormatError(
                "Invalid message format: IV or encrypted data is empty"
            )
        
        try:
            # Base64 decode - add padding if needed
            # Android uses NO_WRAP which strips padding
            def add_padding(s: str) -> str:
                missing_padding = len(s) % 4
                if missing_padding:
                    return s + '=' * (4 - missing_padding)
                return s
            
            iv = base64.b64decode(add_padding(iv_b64))
            encrypted = base64.b64decode(add_padding(encrypted_b64))
            
            # Validate decoded sizes
            if len(iv) != self.AES_IV_SIZE:
                raise InvalidMessageFormatError(
                    f"Decoded IV size is {len(iv)} bytes, expected {self.AES_IV_SIZE} bytes"
                )
            
            return self.decrypt(iv, encrypted)
            
        except base64.binascii.Error as e:
            raise InvalidMessageFormatError(
                f"Invalid base64 encoding: {e}"
            ) from e
        except DecryptionError:
            raise
        except Exception as e:
            raise DecryptionError(f"Failed to decrypt from transmission: {e}") from e
    
    @staticmethod
    def calculate_hash(content: str) -> str:
        """
        Calculate SHA-256 hash of content in lowercase hex format.
        
        Matches Android ClipboardSyncService.calculateHash() which uses %02x (lowercase).
        
        Args:
            content: String to hash
            
        Returns:
            Lowercase hexadecimal hash string
        """
        if not isinstance(content, str):
            raise ValueError(f"Content must be string, got {type(content).__name__}")
        
        digest = hashlib.sha256(content.encode('utf-8')).digest()
        return digest.hex()  # lowercase hex
