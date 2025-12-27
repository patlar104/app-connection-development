"""RSA-based key exchange with improved error handling."""
import base64
import logging
from pathlib import Path
from typing import Optional

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa
from cryptography.hazmat.primitives import hashes

from ..exceptions import KeyExchangeError, InvalidKeyError

logger = logging.getLogger(__name__)


class RSAKeyExchange:
    """
    RSA-based key exchange for establishing shared AES keys.
    
    Improvements:
    - Better error handling
    - Key validation
    - Better logging
    - Support for different RSA key sizes
    """
    
    DEFAULT_KEY_SIZE = 2048  # bits
    MIN_KEY_SIZE = 1024
    MAX_KEY_SIZE = 4096
    
    def __init__(self, private_key_file: Optional[Path] = None, key_size: int = DEFAULT_KEY_SIZE):
        """
        Initialize RSA key exchange.
        
        Args:
            private_key_file: Path to RSA private key file (PEM format)
            key_size: RSA key size in bits (if generating new key)
        """
        self.private_key_file = private_key_file
        self.key_size = key_size
        self._private_key: Optional[rsa.RSAPrivateKey] = None
        
        if private_key_file and private_key_file.exists():
            self._load_private_key()
    
    def _load_private_key(self) -> None:
        """Load RSA private key from file."""
        if not self.private_key_file or not self.private_key_file.exists():
            raise KeyExchangeError(
                f"RSA private key file not found: {self.private_key_file}"
            )
        
        try:
            with open(self.private_key_file, "rb") as f:
                key_data = f.read()
                if not key_data:
                    raise KeyExchangeError(
                        f"RSA private key file is empty: {self.private_key_file}"
                    )
                
                self._private_key = serialization.load_pem_private_key(
                    key_data,
                    password=None
                )
                
                # Validate key size
                key_size = self._private_key.key_size
                if key_size < self.MIN_KEY_SIZE:
                    raise KeyExchangeError(
                        f"RSA key size {key_size} bits is too small "
                        f"(minimum: {self.MIN_KEY_SIZE} bits)"
                    )
                
                logger.info(
                    f"Loaded RSA private key from {self.private_key_file} "
                    f"(key size: {key_size} bits)"
                )
                
        except FileNotFoundError:
            raise KeyExchangeError(
                f"RSA private key file not found: {self.private_key_file}"
            )
        except ValueError as e:
            raise KeyExchangeError(
                f"Invalid RSA private key file: {e}"
            ) from e
        except Exception as e:
            raise KeyExchangeError(
                f"Failed to load RSA private key: {e}"
            ) from e
    
    def generate_key_pair(self) -> tuple[rsa.RSAPrivateKey, rsa.RSAPublicKey]:
        """
        Generate a new RSA key pair.
        
        Returns:
            Tuple of (private_key, public_key)
        """
        if self.key_size < self.MIN_KEY_SIZE or self.key_size > self.MAX_KEY_SIZE:
            raise ValueError(
                f"RSA key size must be between {self.MIN_KEY_SIZE} and {self.MAX_KEY_SIZE} bits"
            )
        
        logger.info(f"Generating RSA key pair (key size: {self.key_size} bits)...")
        
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=self.key_size
        )
        
        public_key = private_key.public_key()
        
        logger.info("RSA key pair generated successfully")
        
        return private_key, public_key
    
    def get_public_key_pem(self) -> str:
        """
        Get public key in PEM format.
        
        Returns:
            Public key as PEM string
            
        Raises:
            KeyExchangeError: If private key is not loaded
        """
        if not self._private_key:
            raise KeyExchangeError("RSA private key not loaded")
        
        public_key = self._private_key.public_key()
        
        pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        
        return pem.decode('ascii')
    
    def get_public_key_base64(self) -> str:
        """
        Get public key as base64-encoded string (for QR code).
        
        Returns:
            Base64-encoded public key
            
        Raises:
            KeyExchangeError: If private key is not loaded
        """
        pem = self.get_public_key_pem()
        # Remove PEM headers and newlines
        pem_content = ''.join(pem.split('\n')[1:-2])  # Remove BEGIN/END lines and newlines
        return pem_content
    
    def decrypt_aes_key(self, encrypted_key_b64: str) -> bytes:
        """
        Decrypt AES key from base64-encoded RSA-encrypted data.
        
        Args:
            encrypted_key_b64: Base64-encoded RSA-encrypted AES key
            
        Returns:
            Decrypted AES key (32 bytes)
            
        Raises:
            KeyExchangeError: If decryption fails
            InvalidKeyError: If decrypted key is invalid size
        """
        if not self._private_key:
            raise KeyExchangeError("RSA private key not loaded")
        
        try:
            # Add padding for Base64 decode (Android uses NO_WRAP which strips padding)
            def add_padding(s: str) -> str:
                missing_padding = len(s) % 4
                if missing_padding:
                    return s + '=' * (4 - missing_padding)
                return s
            
            # Decode base64
            encrypted_key = base64.b64decode(add_padding(encrypted_key_b64))
            
            logger.debug(
                f"Decrypting AES key: encrypted length={len(encrypted_key)} bytes"
            )
            
            # Decrypt using RSA-OAEP with SHA-256
            aes_key = self._private_key.decrypt(
                encrypted_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
            
            # Validate decrypted AES key size (must be 32 bytes for AES-256)
            if len(aes_key) != 32:
                raise InvalidKeyError(
                    f"Invalid AES key size: {len(aes_key)} bytes (expected 32 bytes)"
                )
            
            logger.info("AES key decrypted successfully")
            return aes_key
            
        except base64.binascii.Error as e:
            raise KeyExchangeError(f"Invalid base64 encoding: {e}") from e
        except ValueError as e:
            raise KeyExchangeError(f"RSA decryption failed: {e}") from e
        except Exception as e:
            raise KeyExchangeError(f"Failed to decrypt AES key: {e}") from e
