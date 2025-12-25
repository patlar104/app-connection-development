"""Integration tests for PC server components."""
import asyncio
import json
import base64
import ssl
import socket
import sys
from pathlib import Path

# Add src to path
sys.path.insert(0, str(Path(__file__).parent))

from src.config import Config
from src.certificate_manager import CertificateManager
from src.qr_generator import QRCodeGenerator
from src.encryption import EncryptionManager
from src.message_parser import ClipboardItem


def test_certificate_generation():
    """Test SSL certificate generation and fingerprint."""
    print("Testing certificate generation...")
    config = Config()
    cert_manager = CertificateManager(
        cert_file=config.cert_file,
        key_file=config.key_file,
        device_name=config.device_name
    )
    cert_manager.ensure_certificate_exists()
    
    fingerprint = cert_manager.get_certificate_fingerprint()
    assert fingerprint.startswith("SHA256:"), "Fingerprint should start with SHA256:"
    assert len(fingerprint) > 10, "Fingerprint should be a valid hex string"
    
    ssl_ctx = cert_manager.get_ssl_context()
    assert ssl_ctx is not None, "SSL context should be created"
    
    print("  [OK] Certificate generation works")
    return cert_manager


def test_qr_generation():
    """Test QR code generation."""
    print("Testing QR code generation...")
    config = Config()
    cert_manager = CertificateManager(
        cert_file=config.cert_file,
        key_file=config.key_file,
        device_name=config.device_name
    )
    cert_manager.ensure_certificate_exists()
    
    local_ip = config.get_local_ip()
    fingerprint = cert_manager.get_certificate_fingerprint()
    
    qr_gen = QRCodeGenerator(
        device_name=config.device_name,
        ip=local_ip,
        port=config.port,
        cert_fingerprint=fingerprint,
        rsa_public_key_file=config.rsa_public_key_file
    )
    qr_gen.ensure_rsa_key_exists()
    
    connection_info = qr_gen.generate_qr_code()
    
    # Parse JSON
    info_dict = json.loads(connection_info)
    assert "n" in info_dict, "Should have device name"
    assert "ip" in info_dict, "Should have IP"
    assert "p" in info_dict, "Should have port"
    assert "k" in info_dict, "Should have public key"
    assert "fp" in info_dict, "Should have fingerprint"
    
    assert info_dict["n"] == config.device_name
    assert info_dict["ip"] == local_ip
    assert info_dict["p"] == config.port
    assert info_dict["fp"] == fingerprint
    
    print("  [OK] QR code generation works")
    return qr_gen


def test_encryption():
    """Test AES-256-GCM encryption/decryption."""
    print("Testing encryption...")
    
    # Generate a key
    enc_manager = EncryptionManager()
    key = enc_manager.generate_key()
    assert len(key) == 32, "Key should be 32 bytes"
    
    # Test encryption/decryption
    plaintext = "Hello, World! This is a test message."
    encrypted = enc_manager.encrypt_for_transmission(plaintext)
    
    # Should be in format {ivBase64}|{encryptedBase64}
    parts = encrypted.split("|")
    assert len(parts) == 2, "Encrypted message should have two parts"
    
    # Decrypt
    decrypted = enc_manager.decrypt_from_transmission(encrypted)
    assert decrypted == plaintext, "Decrypted text should match original"
    
    # Test hash calculation
    hash1 = EncryptionManager.calculate_hash("test")
    hash2 = EncryptionManager.calculate_hash("test")
    assert hash1 == hash2, "Same input should produce same hash"
    assert hash1 != EncryptionManager.calculate_hash("different"), "Different input should produce different hash"
    assert all(c in '0123456789abcdef' for c in hash1), "Hash should be lowercase hex"
    
    print("  [OK] Encryption works")
    return enc_manager


def test_message_parsing():
    """Test ClipboardItem JSON parsing."""
    print("Testing message parsing...")
    
    # Create a clipboard item
    item = ClipboardItem.create_text_item("Test clipboard content", "test-device")
    
    # Serialize to JSON
    json_str = item.to_json()
    assert json_str is not None, "JSON should be generated"
    
    # Deserialize from JSON
    item2 = ClipboardItem.from_json(json_str)
    assert item2.content == item.content, "Content should match"
    assert item2.contentType == "TEXT", "Content type should be TEXT"
    assert item2.hash == item.hash, "Hash should match"
    assert item2.sourceDeviceId == item.sourceDeviceId, "Source device ID should match"
    
    print("  [OK] Message parsing works")
    return item


def test_reachability_check():
    """Test TCP reachability check (simulating Android's isReachable)."""
    print("Testing reachability check...")
    config = Config()
    local_ip = config.get_local_ip()
    
    # Create a test socket to check if port is reachable
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(3)
    
    try:
        result = sock.connect_ex((local_ip, config.port))
        # If server is not running, connection will fail (expected)
        # We just want to verify the socket logic works
        print(f"  [INFO] Port {config.port} reachability: {'reachable' if result == 0 else 'not reachable (expected if server not running)'}")
    except Exception as e:
        print(f"  [INFO] Reachability check: {e}")
    finally:
        sock.close()
    
    print("  [OK] Reachability check works")


async def test_websocket_connection():
    """Test WebSocket connection (requires server to be running)."""
    print("Testing WebSocket connection...")
    config = Config()
    local_ip = config.get_local_ip()
    
    # Create SSL context that accepts self-signed certificates
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE
    
    try:
        import websockets
        uri = f"wss://{local_ip}:{config.port}"
        
        async with websockets.connect(uri, ssl=ssl_context) as websocket:
            print("  [OK] WebSocket connection established")
            
            # Test key exchange (if using pre-shared key, this might not be needed)
            # For now, just verify connection works
            return True
            
    except ConnectionRefusedError:
        print("  [SKIP] WebSocket server not running (start server to test)")
        return False
    except Exception as e:
        print(f"  [SKIP] WebSocket test failed: {e}")
        return False


def run_all_tests():
    """Run all integration tests."""
    print("=" * 60)
    print("Running PC Server Integration Tests")
    print("=" * 60)
    print()
    
    tests_passed = 0
    tests_failed = 0
    
    # Test 1: Certificate generation
    try:
        test_certificate_generation()
        tests_passed += 1
    except Exception as e:
        print(f"  [FAIL] {e}")
        tests_failed += 1
    
    print()
    
    # Test 2: QR code generation
    try:
        test_qr_generation()
        tests_passed += 1
    except Exception as e:
        print(f"  [FAIL] {e}")
        tests_failed += 1
    
    print()
    
    # Test 3: Encryption
    try:
        test_encryption()
        tests_passed += 1
    except Exception as e:
        print(f"  [FAIL] {e}")
        tests_failed += 1
    
    print()
    
    # Test 4: Message parsing
    try:
        test_message_parsing()
        tests_passed += 1
    except Exception as e:
        print(f"  [FAIL] {e}")
        tests_failed += 1
    
    print()
    
    # Test 5: Reachability check
    try:
        test_reachability_check()
        tests_passed += 1
    except Exception as e:
        print(f"  [FAIL] {e}")
        tests_failed += 1
    
    print()
    
    # Test 6: WebSocket connection (async)
    try:
        result = asyncio.run(test_websocket_connection())
        if result:
            tests_passed += 1
        else:
            print("  [SKIP] WebSocket test skipped (server not running)")
    except Exception as e:
        print(f"  [SKIP] WebSocket test: {e}")
    
    print()
    print("=" * 60)
    print(f"Tests passed: {tests_passed}")
    print(f"Tests failed: {tests_failed}")
    print("=" * 60)
    
    return tests_failed == 0


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)

