"""Platform-specific clipboard monitoring."""
import platform
import time
import threading
from typing import Optional, Callable
import pyperclip


class ClipboardMonitor:
    """Monitors PC clipboard for changes."""
    
    def __init__(self, on_change: Callable[[str], None], debounce_ms: int = 500):
        """
        Initialize clipboard monitor.
        
        Args:
            on_change: Callback function called when clipboard changes (receives clipboard text)
            debounce_ms: Debounce time in milliseconds to avoid rapid-fire changes
        """
        self.on_change = on_change
        self.debounce_ms = debounce_ms / 1000.0  # Convert to seconds
        self._last_content: Optional[str] = None
        self._last_hash: Optional[str] = None
        self._running = False
        self._thread: Optional[threading.Thread] = None
        self._lock = threading.Lock()
        
    def start(self) -> None:
        """Start monitoring clipboard."""
        if self._running:
            return
            
        self._running = True
        self._thread = threading.Thread(target=self._monitor_loop, daemon=True)
        self._thread.start()
        print("Clipboard monitoring started")
        
    def stop(self) -> None:
        """Stop monitoring clipboard."""
        self._running = False
        if self._thread:
            self._thread.join(timeout=1.0)
        print("Clipboard monitoring stopped")
        
    def _monitor_loop(self) -> None:
        """Main monitoring loop."""
        while self._running:
            try:
                # Get current clipboard content
                current_content = pyperclip.paste()
                
                if current_content and current_content != self._last_content:
                    # Calculate hash to avoid duplicate notifications
                    import hashlib
                    current_hash = hashlib.sha256(current_content.encode('utf-8')).hexdigest()
                    
                    if current_hash != self._last_hash:
                        self._last_content = current_content
                        self._last_hash = current_hash
                        
                        # Debounce: wait a bit before notifying
                        time.sleep(self.debounce_ms)
                        
                        # Check if content still changed after debounce
                        if pyperclip.paste() == current_content:
                            self.on_change(current_content)
                            
            except Exception as e:
                print(f"Error monitoring clipboard: {e}")
                
            # Check every 100ms
            time.sleep(0.1)
            
    def get_current_content(self) -> Optional[str]:
        """Get current clipboard content."""
        try:
            return pyperclip.paste()
        except Exception:
            return None
            
    def set_content(self, content: str) -> None:
        """
        Set clipboard content (used to prevent loops).
        
        This updates internal state without triggering change callback.
        """
        with self._lock:
            self._last_content = content
            import hashlib
            self._last_hash = hashlib.sha256(content.encode('utf-8')).hexdigest()
            pyperclip.copy(content)

