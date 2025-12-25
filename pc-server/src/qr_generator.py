"""QR code generation with connection info."""
import json
import base64
import qrcode
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from typing import Optional


class QRCodeGenerator:
    """Generates QR codes with connection information."""
    
    def __init__(self, device_name: str, ip: str, port: int, 
                 cert_fingerprint: str, rsa_public_key_file: Path):
        self.device_name = device_name
        self.ip = ip
        self.port = port
        self.cert_fingerprint = cert_fingerprint
        self.rsa_public_key_file = rsa_public_key_file
        
    def ensure_rsa_key_exists(self) -> None:
        """Generate RSA key pair if it doesn't exist."""
        if not self.rsa_public_key_file.exists():
            self._generate_rsa_key_pair()
            
    def _generate_rsa_key_pair(self) -> None:
        """Generate RSA key pair for key exchange."""
        # Generate private key
        private_key = rsa.generate_private_key(
            public_exponent=65537,
            key_size=2048
        )
        
        # Get public key
        public_key = private_key.public_key()
        
        # Save private key
        private_key_file = self.rsa_public_key_file.parent / "rsa_private.pem"
        with open(private_key_file, "wb") as f:
            f.write(private_key.private_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PrivateFormat.PKCS8,
                encryption_algorithm=serialization.NoEncryption()
            ))
            
        # Save public key
        with open(self.rsa_public_key_file, "wb") as f:
            f.write(public_key.public_bytes(
                encoding=serialization.Encoding.PEM,
                format=serialization.PublicFormat.SubjectPublicKeyInfo
            ))
            
        print(f"Generated RSA key pair: {self.rsa_public_key_file}")
        
    def get_public_key_base64(self) -> str:
        """Get RSA public key as Base64 string for QR code."""
        if not self.rsa_public_key_file.exists():
            self._generate_rsa_key_pair()
            
        with open(self.rsa_public_key_file, "rb") as f:
            public_key_pem = f.read()
            
        # Convert PEM to Base64 (remove headers and newlines)
        public_key_b64 = base64.b64encode(public_key_pem).decode('ascii')
        return public_key_b64
        
    def generate_connection_info_json(self) -> str:
        """
        Generate JSON connection info matching QrConnectionInfo format.
        
        Format: {"n": name, "ip": ip, "p": port, "k": publicKey, "fp": certFingerprint}
        """
        public_key_b64 = self.get_public_key_base64()
        
        connection_info = {
            "n": self.device_name,
            "ip": self.ip,
            "p": self.port,
            "k": public_key_b64,
            "fp": self.cert_fingerprint
        }
        
        # Compact JSON (no spaces) to minimize QR code size
        return json.dumps(connection_info, separators=(',', ':'))
        
    def generate_qr_code(self, output_file: Optional[Path] = None) -> str:
        """
        Generate QR code image and return connection info JSON.
        
        Args:
            output_file: Optional path to save QR code image
            
        Returns:
            Connection info JSON string
        """
        connection_info_json = self.generate_connection_info_json()
        
        # Create QR code
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=2,
            border=2,
        )
        qr.add_data(connection_info_json)
        qr.make(fit=True)
        
        # Create image
        img = qr.make_image(fill_color="black", back_color="white")
        
        # Always save to default location if not specified
        if output_file is None:
            output_file = self.rsa_public_key_file.parent / "qr_code.png"
        
        img.save(output_file)
        print(f"\nQR code saved to: {output_file}")
        print(f"You can open this image file to scan with your Android app!\n")
        
        # Also display in terminal using ASCII
        self._print_qr_ascii(qr)
            
        return connection_info_json
        
    def _print_qr_ascii(self, qr: qrcode.QRCode) -> None:
        """Print QR code as ASCII art in terminal."""
        print("\n" + "=" * 50)
        print("Scan this QR code with your Android app:")
        print("=" * 50)
        
        # Get matrix
        matrix = qr.get_matrix()
        
        # Print with borders
        print("┌" + "─" * (len(matrix[0]) + 2) + "┐")
        for row in matrix:
            print("│ ", end="")
            for cell in row:
                print("██" if cell else "  ", end="")
            print(" │")
        print("└" + "─" * (len(matrix[0]) + 2) + "┘")
        
        print("\nConnection Info:")
        print(f"  Device: {self.device_name}")
        print(f"  IP: {self.ip}")
        print(f"  Port: {self.port}")
        print(f"  Fingerprint: {self.cert_fingerprint[:20]}...")
        print("=" * 50 + "\n")

