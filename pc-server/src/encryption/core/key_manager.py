"""Key management with lifecycle, derivation, and secure handling."""
import secrets
import hashlib
import time
from typing import Optional, Callable
from enum import Enum

from ..exceptions import InvalidKeyError, KeyManagementError


class KeyDerivationMethod(Enum):
    """Key derivation methods."""
    NONE = "none"  # Use key directly (current behavior)
    PBKDF2 = "pbkdf2"  # PBKDF2 with SHA-256
    # ARGON2 = "argon2"  # Argon2 (requires argon2-cffi, future enhancement)


class KeyManager:
    """
    Manages encryption keys with lifecycle, derivation, and secure handling.
    
    Features:
    - Key validation
    - Key derivation (optional)
    - Key expiration
    - Secure key clearing
    - Key rotation support
    """
    
    def __init__(
        self,
        key: Optional[bytes] = None,
        derivation_method: KeyDerivationMethod = KeyDerivationMethod.NONE,
        expiration_seconds: Optional[int] = None,
        on_expiration: Optional[Callable[[], None]] = None
    ):
        """
        Initialize key manager.
        
        Args:
            key: Raw encryption key (32 bytes for AES-256)
            derivation_method: Method for key derivation (if needed)
            expiration_seconds: Key expiration time in seconds (None = no expiration)
            on_expiration: Callback when key expires
        """
        self._raw_key: Optional[bytes] = None
        self._derived_key: Optional[bytes] = None
        self._derivation_method = derivation_method
        self._expiration_time: Optional[float] = None
        self._on_expiration = on_expiration
        self._created_at: Optional[float] = None
        
        if key:
            self.set_key(key)
            
        if expiration_seconds:
            self.set_expiration(expiration_seconds)
    
    def set_key(self, key: bytes) -> None:
        """
        Set the encryption key.
        
        Args:
            key: AES-256 key (32 bytes)
            
        Raises:
            InvalidKeyError: If key is invalid
        """
        if not isinstance(key, bytes):
            raise InvalidKeyError(f"Key must be bytes, got {type(key).__name__}")
        
        if len(key) != 32:
            raise InvalidKeyError(
                f"AES-256 key must be 32 bytes, got {len(key)} bytes"
            )
        
        # Clear old key from memory
        self._clear_key()
        
        # Store raw key
        self._raw_key = key
        self._created_at = time.time()
        
        # Derive key if needed
        if self._derivation_method != KeyDerivationMethod.NONE:
            self._derive_key()
        else:
            self._derived_key = key
    
    def get_key(self) -> bytes:
        """
        Get the encryption key (derived if derivation is enabled).
        
        Returns:
            Encryption key (32 bytes)
            
        Raises:
            KeyManagementError: If key is not set or expired
        """
        if self._derived_key is None:
            raise KeyManagementError("Encryption key not set")
        
        # Check expiration
        if self._expiration_time and time.time() > self._expiration_time:
            if self._on_expiration:
                try:
                    self._on_expiration()
                except Exception:
                    pass  # Don't fail if callback fails
            
            raise KeyManagementError("Encryption key has expired")
        
        return self._derived_key
    
    def generate_key(self) -> bytes:
        """
        Generate a new random AES-256 key.
        
        Returns:
            New 32-byte encryption key
        """
        key = secrets.token_bytes(32)
        self.set_key(key)
        return key
    
    def derive_key(self, salt: Optional[bytes] = None, iterations: int = 100000) -> bytes:
        """
        Derive a key using PBKDF2 (if derivation is enabled).
        
        Args:
            salt: Salt for key derivation (generated if None)
            iterations: Number of PBKDF2 iterations
            
        Returns:
            Derived key (32 bytes)
            
        Raises:
            KeyManagementError: If raw key is not set
        """
        if self._raw_key is None:
            raise KeyManagementError("Raw key must be set before derivation")
        
        if self._derivation_method == KeyDerivationMethod.NONE:
            return self._raw_key
        
        if self._derivation_method == KeyDerivationMethod.PBKDF2:
            if salt is None:
                salt = secrets.token_bytes(16)  # 128-bit salt
            
            # Use PBKDF2 with SHA-256
            derived = hashlib.pbkdf2_hmac(
                'sha256',
                self._raw_key,
                salt,
                iterations
            )
            
            # Ensure 32 bytes (PBKDF2-SHA256 produces 32 bytes by default)
            if len(derived) != 32:
                # Truncate or pad if needed (shouldn't happen with SHA-256)
                derived = derived[:32] if len(derived) > 32 else derived + b'\x00' * (32 - len(derived))
            
            return derived
        
        raise KeyManagementError(f"Unsupported derivation method: {self._derivation_method}")
    
    def _derive_key(self) -> None:
        """Internal method to derive key from raw key."""
        if self._raw_key is None:
            return
        
        if self._derivation_method == KeyDerivationMethod.NONE:
            self._derived_key = self._raw_key
        else:
            self._derived_key = self.derive_key()
    
    def set_expiration(self, seconds: int) -> None:
        """
        Set key expiration time.
        
        Args:
            seconds: Expiration time in seconds from now
        """
        if self._created_at is None:
            self._created_at = time.time()
        
        self._expiration_time = self._created_at + seconds
    
    def is_expired(self) -> bool:
        """Check if key is expired."""
        if self._expiration_time is None:
            return False
        return time.time() > self._expiration_time
    
    def get_age_seconds(self) -> Optional[float]:
        """Get key age in seconds."""
        if self._created_at is None:
            return None
        return time.time() - self._created_at
    
    def rotate_key(self) -> bytes:
        """
        Generate a new key and clear the old one.
        
        Returns:
            New encryption key
        """
        old_key = self._derived_key
        new_key = self.generate_key()
        
        # Clear old key
        if old_key:
            self._clear_bytes(old_key)
        
        return new_key
    
    def _clear_key(self) -> None:
        """Clear key from memory."""
        if self._raw_key:
            self._clear_bytes(self._raw_key)
            self._raw_key = None
        
        if self._derived_key:
            self._clear_bytes(self._derived_key)
            self._derived_key = None
    
    @staticmethod
    def _clear_bytes(data: bytes) -> None:
        """
        Attempt to clear bytes from memory.
        
        Note: Python doesn't guarantee memory clearing, but we try.
        For production, consider using libraries that provide secure memory.
        """
        # In Python, we can't truly clear memory, but we can try to overwrite
        # For production use, consider using libraries like `pynacl` or `cryptography`
        # that provide secure memory handling
        try:
            # Create a new bytes object with zeros
            zero_bytes = bytes(len(data))
            # This doesn't actually clear the old memory, but it's the best we can do in Python
            del data
        except Exception:
            pass
    
    def __del__(self):
        """Cleanup: clear keys when object is destroyed."""
        self._clear_key()
