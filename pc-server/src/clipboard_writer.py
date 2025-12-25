"""Platform-specific clipboard writing."""
import pyperclip
from typing import Optional


class ClipboardWriter:
    """Writes content to PC clipboard."""
    
    @staticmethod
    def write_text(text: str) -> bool:
        """
        Write text to clipboard.
        
        Args:
            text: Text content to write
            
        Returns:
            True if successful, False otherwise
        """
        try:
            pyperclip.copy(text)
            return True
        except Exception as e:
            print(f"Error writing to clipboard: {e}")
            return False
            
    @staticmethod
    def write_image(image_data: bytes) -> bool:
        """
        Write image to clipboard (platform-specific).
        
        Args:
            image_data: Image bytes (PNG, JPEG, etc.)
            
        Returns:
            True if successful, False otherwise
        """
        # For now, images are not supported
        # Would need platform-specific implementations:
        # - Windows: win32clipboard with CF_DIB
        # - Linux: xclip with image MIME types
        # - macOS: NSPasteboard with image types
        print("Image clipboard writing not yet implemented")
        return False
        
    @staticmethod
    def get_current_text() -> Optional[str]:
        """Get current clipboard text content."""
        try:
            return pyperclip.paste()
        except Exception:
            return None

