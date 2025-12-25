"""Configuration management for PC server."""
import os
from pathlib import Path
from typing import Optional

# Default configuration
DEFAULT_PORT = 8765
DEFAULT_DEVICE_NAME = "My-PC"
CERT_DIR = Path(__file__).parent.parent / "certs"
CERT_DIR.mkdir(exist_ok=True)

CERT_FILE = CERT_DIR / "server.crt"
KEY_FILE = CERT_DIR / "server.key"
RSA_KEY_FILE = CERT_DIR / "rsa_private.pem"
RSA_PUBLIC_KEY_FILE = CERT_DIR / "rsa_public.pem"

# mDNS configuration
MDNS_SERVICE_TYPE = "_appconnect._tcp"
MDNS_APP_ID = "dev.appconnect"

# Encryption configuration
AES_KEY_SIZE = 32  # 256 bits
AES_IV_SIZE = 12  # 96 bits
AES_TAG_SIZE = 16  # 128 bits

# Network configuration
REACHABILITY_TIMEOUT = 3  # seconds

# Pre-shared key (for testing without Android key exchange)
# If set, this key will be used instead of RSA key exchange
# Format: base64-encoded 32-byte key
PRE_SHARED_KEY = os.getenv("APPCONNECT_PRE_SHARED_KEY", None)


class Config:
    """Server configuration."""
    
    def __init__(self):
        self.port: int = int(os.getenv("APPCONNECT_PORT", DEFAULT_PORT))
        self.device_name: str = os.getenv("APPCONNECT_DEVICE_NAME", DEFAULT_DEVICE_NAME)
        self.cert_file: Path = Path(os.getenv("APPCONNECT_CERT_FILE", str(CERT_FILE)))
        self.key_file: Path = Path(os.getenv("APPCONNECT_KEY_FILE", str(KEY_FILE)))
        self.rsa_key_file: Path = Path(os.getenv("APPCONNECT_RSA_KEY_FILE", str(RSA_KEY_FILE)))
        self.rsa_public_key_file: Path = Path(os.getenv("APPCONNECT_RSA_PUBLIC_KEY_FILE", str(RSA_PUBLIC_KEY_FILE)))
        
    def get_local_ip(self) -> Optional[str]:
        """Get local IP address for primary network interface."""
        import socket
        import netifaces
        
        try:
            # Get default gateway interface
            gateways = netifaces.gateways()
            default_interface = gateways['default'][netifaces.AF_INET][1]
            
            # Get IP address for that interface
            addresses = netifaces.ifaddresses(default_interface)
            if netifaces.AF_INET in addresses:
                ip = addresses[netifaces.AF_INET][0]['addr']
                return ip
        except Exception:
            pass
        
        # Fallback: try to get IP by connecting to external address
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return None

