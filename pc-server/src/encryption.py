"""AES-256-GCM encryption/decryption matching Android implementation.

This module provides backward compatibility with the original encryption API.
It now uses the refactored encryption module internally.

For new code, consider using the new modular API:
    from src.encryption import EncryptionManager, KeyManager, RSAKeyExchange
"""
from typing import Optional

# Import from new refactored module for backward compatibility
from .encryption.core.encryption_manager import EncryptionManager as _EncryptionManager


class EncryptionManager(_EncryptionManager):
    """
    Manages AES-256-GCM encryption matching Android EncryptionManager.
    
    This class is a backward-compatible wrapper around the refactored
    EncryptionManager. All existing code will continue to work.
    
    For new code with advanced features, use:
        from src.encryption import EncryptionManager, KeyManager
    """
    
    def __init__(self, key: Optional[bytes] = None):
        """
        Initialize encryption manager.
        
        Args:
            key: AES-256 key (32 bytes). If None, key must be set via set_key().
        """
        # Call parent with direct key (backward compatibility)
        super().__init__(key=key)

