"""Custom exceptions for encryption operations."""


class EncryptionError(Exception):
    """Base exception for encryption-related errors."""
    pass


class DecryptionError(EncryptionError):
    """Exception raised when decryption fails."""
    
    def __init__(self, message: str, original_error: Exception = None):
        super().__init__(message)
        self.original_error = original_error


class KeyExchangeError(EncryptionError):
    """Exception raised when key exchange fails."""
    pass


class KeyManagementError(EncryptionError):
    """Exception raised when key management operations fail."""
    pass


class InvalidKeyError(KeyManagementError):
    """Exception raised when an invalid key is provided."""
    pass


class InvalidMessageFormatError(DecryptionError):
    """Exception raised when message format is invalid."""
    pass
