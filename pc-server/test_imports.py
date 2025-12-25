"""Quick test to verify all imports work correctly."""
import sys

def test_imports():
    """Test that all modules can be imported."""
    try:
        from src import config
        print("[OK] config")
        
        from src import certificate_manager
        print("[OK] certificate_manager")
        
        from src import encryption
        print("[OK] encryption")
        
        from src import qr_generator
        print("[OK] qr_generator")
        
        from src import mdns_service
        print("[OK] mdns_service")
        
        from src import websocket_server
        print("[OK] websocket_server")
        
        from src import clipboard_monitor
        print("[OK] clipboard_monitor")
        
        from src import clipboard_writer
        print("[OK] clipboard_writer")
        
        from src import message_parser
        print("[OK] message_parser")
        
        from src import server
        print("[OK] server")
        
        print("\nAll imports successful!")
        return True
        
    except ImportError as e:
        print(f"\n[ERROR] Import error: {e}")
        return False
    except Exception as e:
        print(f"\n[ERROR] Error: {e}")
        import traceback
        traceback.print_exc()
        return False

if __name__ == "__main__":
    success = test_imports()
    sys.exit(0 if success else 1)

