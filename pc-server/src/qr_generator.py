"""QR code generation with connection info."""
import json
import base64
import qrcode
from pathlib import Path
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from typing import Optional
import multiprocessing
import sys

# Try importing Tkinter for GUI display (built-in to Python)
try:
    import tkinter as tk
    from PIL import ImageTk
    TKINTER_AVAILABLE = True
except ImportError:
    TKINTER_AVAILABLE = False


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
        """
        Get RSA public key as Base64-encoded DER bytes for QR code.
        
        Android's X509EncodedKeySpec expects DER-encoded bytes (raw ASN.1),
        not PEM format. This method extracts the DER bytes from the PEM file
        and Base64 encodes them.
        """
        if not self.rsa_public_key_file.exists():
            self._generate_rsa_key_pair()
            
        # Load the PEM public key file
        with open(self.rsa_public_key_file, "rb") as f:
            public_key_pem = f.read()
        
        # Parse the PEM format to get the public key object
        public_key = serialization.load_pem_public_key(public_key_pem)
        
        # Extract DER-encoded bytes (raw ASN.1 format, no PEM headers)
        # This is what Android's X509EncodedKeySpec expects
        der_bytes = public_key.public_bytes(
            encoding=serialization.Encoding.DER,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        
        # Base64 encode the DER bytes (Android will decode with Base64.NO_WRAP)
        public_key_b64 = base64.b64encode(der_bytes).decode('ascii')
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
        
    def _create_qr_image(self):
        """
        Create QR code image from connection info.
        
        Returns:
            Tuple of (QRCode object, PIL Image)
        """
        connection_info_json = self.generate_connection_info_json()
        
        # Create QR code with optimized settings for faster scanning:
        # - box_size=4: Larger modules for easier detection at various distances
        # - border=4: Standard quiet zone (4 modules on all sides) for better detection
        # - ERROR_CORRECT_L: Low error correction (7% recovery) is sufficient and faster to scan
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=4,  # Increased from 2 to 4 for better scanning at distance
            border=4,    # Increased from 2 to 4 for standard quiet zone
        )
        qr.add_data(connection_info_json)
        qr.make(fit=True)
        
        # Create image
        img = qr.make_image(fill_color="black", back_color="white")
        
        return qr, img
    
    def generate_qr_code(self, output_file: Optional[Path] = None, save_file: bool = False) -> str:
        """
        Generate QR code image and return connection info JSON.
        
        Args:
            output_file: Optional path to save QR code image (only used if save_file=True)
            save_file: If True, save QR code to file (default: False for security)
            
        Returns:
            Connection info JSON string
        """
        connection_info_json = self.generate_connection_info_json()
        qr, img = self._create_qr_image()
        
        # Optionally save to file (disabled by default for security)
        if save_file:
            if output_file is None:
                output_file = self.rsa_public_key_file.parent / "qr_code.png"
            img.save(output_file)
            print(f"\nQR code saved to: {output_file}")
        else:
            print("\nQR code generated (not saved to file for security)")
        
        # Display in terminal using ASCII (informational only)
        self._print_qr_ascii(qr)
            
        return connection_info_json
    
    def display_qr_code_window(self) -> str:
        """
        Generate and display QR code in a GUI popup window (secure, no file saved).
        
        Uses a separate process for the GUI window for cross-platform compatibility:
        - macOS: Required for main thread compatibility
        - Windows/Linux: Works seamlessly with multiprocessing
        
        Returns:
            Connection info JSON string
        """
        if not TKINTER_AVAILABLE:
            print("Warning: Tkinter not available, falling back to file-based display")
            return self.generate_qr_code(save_file=True)
        
        connection_info_json = self.generate_connection_info_json()
        qr, img = self._create_qr_image()
        
        # Display connection info in terminal
        print("\n" + "=" * 50)
        print("AppConnect Server Ready")
        print("=" * 50)
        print(f"Device: {self.device_name}")
        print(f"IP: {self.ip}")
        print(f"Port: {self.port}")
        print(f"Fingerprint: {self.cert_fingerprint[:20]}...")
        print("=" * 50)
        print("QR code displayed in popup window. Scan with your Android app!")
        print("=" * 50 + "\n")
        
        # Save image to a temporary in-memory format (BytesIO) for the process
        from io import BytesIO
        img_bytes = BytesIO()
        img.save(img_bytes, format='PNG')
        img_bytes.seek(0)
        img_data = img_bytes.read()
        
        # Start GUI in a separate process for cross-platform compatibility
        # - macOS: Required for main thread compatibility
        # - Windows/Linux: Works seamlessly with multiprocessing
        try:
            process = multiprocessing.Process(
                target=_show_qr_window_process,
                args=(img_data,),
                daemon=True
            )
            process.start()
        except Exception as e:
            print(f"Warning: Could not start GUI window process: {e}")
            print("Falling back to file-based display...")
            return self.generate_qr_code(save_file=True)
        
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


def _show_qr_window_process(img_data: bytes) -> None:
    """
    Display QR code window in a separate process.
    
    This function runs in its own process for cross-platform compatibility:
    - macOS: Required so Tkinter can use the main thread (macOS requirement)
    - Windows/Linux: Works seamlessly with multiprocessing spawn method
    
    The image data is passed as bytes to avoid pickling issues with PIL Image objects.
    """
    try:
        from PIL import Image
        import tkinter as tk
        from PIL import ImageTk
        from io import BytesIO
        
        # Load image from bytes
        img_bytes = BytesIO(img_data)
        img = Image.open(img_bytes)
        
        # Create and configure window
        root = tk.Tk()
        root.title("AppConnect - Scan QR Code")
        root.resizable(False, False)
        
        # Convert PIL Image to PhotoImage for Tkinter
        photo = ImageTk.PhotoImage(img)
        
        # Create label with QR code image
        label = tk.Label(root, image=photo)
        label.image = photo  # Keep a reference to prevent garbage collection
        label.pack(padx=20, pady=20)
        
        # Add instruction text
        instruction = tk.Label(
            root,
            text="Scan this QR code with your Android app",
            font=("Arial", 12)
        )
        instruction.pack(pady=(0, 20))
        
        # Center window on screen
        root.update_idletasks()
        width = root.winfo_width()
        height = root.winfo_height()
        x = (root.winfo_screenwidth() // 2) - (width // 2)
        y = (root.winfo_screenheight() // 2) - (height // 2)
        root.geometry(f"{width}x{height}+{x}+{y}")
        
        # Make window stay on top initially
        root.attributes("-topmost", True)
        root.after(100, lambda: root.attributes("-topmost", False))
        
        # Start event loop (blocks until window is closed)
        root.mainloop()
    except Exception as e:
        print(f"Error displaying QR code window: {e}")
        sys.exit(1)
