"""SSL certificate generation and fingerprint calculation."""
import hashlib
import ipaddress
from pathlib import Path
from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from datetime import datetime, timedelta
import socket


class CertificateManager:
    """Manages SSL certificate generation and fingerprint calculation."""
    
    def __init__(self, cert_file: Path, key_file: Path, device_name: str = "My-PC"):
        self.cert_file = cert_file
        self.key_file = key_file
        self.device_name = device_name
        self._private_key = None
        self._certificate = None
        
    def ensure_certificate_exists(self) -> None:
        """Generate certificate and key if they don't exist."""
        if self.cert_file.exists() and self.key_file.exists():
            self._load_certificate()
        else:
            self._generate_certificate()
            
    def _generate_certificate(self) -> None:
        """Generate a self-signed SSL certificate."""
        # Generate private key
        self._private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048
        )
        
        # Get hostname
        hostname = socket.gethostname()
        
        # Create certificate
        subject = issuer = x509.Name([
            x509.NameAttribute(NameOID.COUNTRY_NAME, "US"),
            x509.NameAttribute(NameOID.STATE_OR_PROVINCE_NAME, "Local"),
            x509.NameAttribute(NameOID.LOCALITY_NAME, "Local"),
            x509.NameAttribute(NameOID.ORGANIZATION_NAME, "AppConnect"),
            x509.NameAttribute(NameOID.COMMON_NAME, hostname),
        ])
        
        self._certificate = x509.CertificateBuilder().subject_name(
            subject
        ).issuer_name(
            issuer
        ).public_key(
            self._private_key.public_key()
        ).serial_number(
            x509.random_serial_number()
        ).not_valid_before(
            datetime.utcnow()
        ).not_valid_after(
            datetime.utcnow() + timedelta(days=3650)  # 10 years
        ).add_extension(
            x509.SubjectAlternativeName([
                x509.DNSName(hostname),
                x509.DNSName("localhost"),
                x509.IPAddress(ipaddress.IPv4Address("127.0.0.1")),
            ]),
            critical=False,
        ).sign(self._private_key, hashes.SHA256())
        
        # Save certificate
        with open(self.cert_file, "wb") as f:
            f.write(self._certificate.public_bytes(serialization.Encoding.PEM))
            
        # Save private key
        with open(self.key_file, "wb") as f:
            f.write(self._private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption()
            ))
            
        print(f"Generated SSL certificate: {self.cert_file}")
        
    def _load_certificate(self) -> None:
        """Load existing certificate and key."""
        with open(self.cert_file, "rb") as f:
            self._certificate = x509.load_pem_x509_certificate(f.read())
            
        with open(self.key_file, "rb") as f:
            self._private_key = serialization.load_pem_private_key(
                f.read(),
                password=None
            )
            
    def get_certificate_fingerprint(self) -> str:
        """Calculate SHA-256 fingerprint in Android format: SHA256:HEX_UPPERCASE."""
        if not self._certificate:
            self._load_certificate()
            
        # Get encoded certificate bytes
        cert_bytes = self._certificate.public_bytes(serialization.Encoding.DER)
        
        # Calculate SHA-256 hash
        digest = hashlib.sha256(cert_bytes).digest()
        
        # Format as uppercase hex string
        hex_fingerprint = digest.hex().upper()
        
        # Return in Android format: SHA256:HEX_UPPERCASE
        return f"SHA256:{hex_fingerprint}"
        
    def get_certificate_path(self) -> Path:
        """Get path to certificate file."""
        return self.cert_file
        
    def get_key_path(self) -> Path:
        """Get path to private key file."""
        return self.key_file
        
    def get_ssl_context(self):
        """Get SSL context for WebSocket server."""
        import ssl
        
        if not self._certificate:
            self._load_certificate()
            
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(
            str(self.cert_file),
            str(self.key_file)
        )
        return context

