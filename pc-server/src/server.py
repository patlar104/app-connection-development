"""Main server entry point."""
import asyncio
import logging
import signal
import sys
from pathlib import Path

from .config import Config
from .certificate_manager import CertificateManager
from .qr_generator import QRCodeGenerator
from .mdns_service import MDNSService
from .websocket_server import WebSocketServer
from .clipboard_monitor import ClipboardMonitor
from .clipboard_writer import ClipboardWriter
from typing import Optional

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class AppConnectServer:
    """Main server class."""
    
    def __init__(self):
        self.config = Config()
        self.cert_manager: Optional[CertificateManager] = None
        self.qr_generator: Optional[QRCodeGenerator] = None
        self.mdns_service: Optional[MDNSService] = None
        self.websocket_server: Optional[WebSocketServer] = None
        self.clipboard_monitor: Optional[ClipboardMonitor] = None
        self.running = False
        self._main_loop: Optional[asyncio.AbstractEventLoop] = None
        
    def initialize(self) -> None:
        """Initialize all server components."""
        logger.info("Initializing AppConnect server...")
        
        # Initialize certificate manager
        self.cert_manager = CertificateManager(
            cert_file=self.config.cert_file,
            key_file=self.config.key_file,
            device_name=self.config.device_name
        )
        self.cert_manager.ensure_certificate_exists()
        
        # Get local IP
        local_ip = self.config.get_local_ip()
        if not local_ip:
            logger.error("Could not determine local IP address")
            sys.exit(1)
            
        logger.info(f"Local IP: {local_ip}")
        
        # Get certificate fingerprint
        cert_fingerprint = self.cert_manager.get_certificate_fingerprint()
        logger.info(f"Certificate fingerprint: {cert_fingerprint}")
        
        # Initialize QR code generator
        self.qr_generator = QRCodeGenerator(
            device_name=self.config.device_name,
            ip=local_ip,
            port=self.config.port,
            cert_fingerprint=cert_fingerprint,
            rsa_public_key_file=self.config.rsa_public_key_file
        )
        self.qr_generator.ensure_rsa_key_exists()
        
        # Generate and display QR code in GUI popup window (secure, no file saved)
        connection_info = self.qr_generator.display_qr_code_window()
        logger.info(f"Connection info: {connection_info}")
        
        # Initialize mDNS service
        self.mdns_service = MDNSService()
        
        # Initialize WebSocket server
        ssl_context = self.cert_manager.get_ssl_context()
        
        # Check for pre-shared key (for testing)
        from .config import PRE_SHARED_KEY
        pre_shared_key = None
        if PRE_SHARED_KEY:
            import base64
            try:
                pre_shared_key = base64.b64decode(PRE_SHARED_KEY)
                if len(pre_shared_key) != 32:
                    logger.warning("Pre-shared key must be 32 bytes, ignoring")
                    pre_shared_key = None
                else:
                    logger.info("Using pre-shared key (RSA key exchange disabled)")
            except Exception as e:
                logger.warning(f"Invalid pre-shared key: {e}")
                
        self.websocket_server = WebSocketServer(
            ssl_context=ssl_context,
            rsa_private_key_file=self.config.rsa_key_file,
            device_id=self.config.device_name,
            on_clipboard_received=self._on_clipboard_received,
            pre_shared_key=pre_shared_key
        )
        
        # Initialize clipboard monitor
        self.clipboard_monitor = ClipboardMonitor(
            on_change=self._on_clipboard_changed,
            debounce_ms=500
        )
        
        logger.info("Server initialization complete")
        
    def _on_clipboard_received(self, content: str) -> None:
        """Callback when clipboard is received from Android."""
        logger.info(f"Clipboard received from Android: {content[:50]}...")
        # Update monitor to prevent loop
        if self.clipboard_monitor:
            self.clipboard_monitor.set_content(content)
            
    def _on_clipboard_changed(self, content: str) -> None:
        """Callback when local clipboard changes."""
        if not self.websocket_server:
            return
            
        logger.info(f"Local clipboard changed: {content[:50]}...")
        
        # Schedule broadcast on the main event loop
        # This callback runs in a separate thread, so we need to schedule
        # the coroutine on the main event loop
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                asyncio.run_coroutine_threadsafe(
                    self.websocket_server.broadcast_clipboard(content),
                    loop
                )
            else:
                # If loop is not running, create a task
                asyncio.create_task(
                    self.websocket_server.broadcast_clipboard(content)
                )
        except RuntimeError:
            # No event loop in this thread, try to get the main loop
            try:
                # Get the loop from the websocket server's context
                # We'll need to store a reference to the loop
                if hasattr(self, '_main_loop') and self._main_loop:
                    asyncio.run_coroutine_threadsafe(
                        self.websocket_server.broadcast_clipboard(content),
                        self._main_loop
                    )
            except Exception as e:
                logger.error(f"Failed to schedule clipboard broadcast: {e}")
        
    async def start(self) -> None:
        """Start all server services."""
        if self.running:
            return
            
        self.running = True
        logger.info("Starting server services...")
        
        # Store reference to main event loop for clipboard monitor callbacks
        self._main_loop = asyncio.get_running_loop()
        
        # Start mDNS broadcasting (async)
        local_ip = self.config.get_local_ip()
        if local_ip:
            await self.mdns_service.start_async(
                name=self.config.device_name,
                port=self.config.port,
                ip=local_ip
            )
            
        # Start clipboard monitoring
        self.clipboard_monitor.start()
        
        # Start WebSocket server (this blocks)
        try:
            await self.websocket_server.start(
                host="0.0.0.0",
                port=self.config.port
            )
        except Exception as e:
            logger.error(f"WebSocket server error: {e}", exc_info=True)
            raise
            
    async def stop_async(self) -> None:
        """Stop all server services (async)."""
        if not self.running:
            return
            
        logger.info("Stopping server...")
        self.running = False
        
        # Stop clipboard monitoring
        if self.clipboard_monitor:
            self.clipboard_monitor.stop()
            
        # Stop mDNS (async)
        if self.mdns_service:
            await self.mdns_service.stop_async()
            
        logger.info("Server stopped")
    
    def stop(self) -> None:
        """Stop all server services (synchronous wrapper)."""
        if not self.running:
            return
            
        # Try to stop asynchronously if we have a loop
        try:
            loop = asyncio.get_event_loop()
            if loop.is_running():
                # Schedule async stop
                asyncio.run_coroutine_threadsafe(self.stop_async(), loop)
            else:
                # Run in current loop
                loop.run_until_complete(self.stop_async())
        except RuntimeError:
            # No event loop, stop synchronously
            logger.info("Stopping server...")
            self.running = False
            if self.clipboard_monitor:
                self.clipboard_monitor.stop()
            logger.info("Server stopped")
        
    def run(self) -> None:
        """Run the server (blocking)."""
        # Initialize
        self.initialize()
        
        # Run server with proper cleanup
        try:
            asyncio.run(self._run_async())
        except KeyboardInterrupt:
            logger.info("Keyboard interrupt received")
        except Exception as e:
            logger.error(f"Server error: {e}", exc_info=True)
        finally:
            # Ensure cleanup happens
            if self.running:
                try:
                    # Try to run cleanup in a new event loop if needed
                    asyncio.run(self.stop_async())
                except RuntimeError:
                    # If we can't create a new loop, just mark as stopped
                    self.running = False
    
    async def _run_async(self) -> None:
        """Run the server asynchronously."""
        try:
            await self.start()
        except asyncio.CancelledError:
            logger.info("Server cancelled")
            raise
        finally:
            # Ensure cleanup on exit
            if self.running:
                await self.stop_async()


def main():
    """Main entry point."""
    # Set multiprocessing start method to 'spawn' on macOS for GUI compatibility
    # This must be done before creating any Process objects
    import multiprocessing
    import platform
    if platform.system() == 'Darwin':  # macOS
        try:
            multiprocessing.set_start_method('spawn', force=True)
        except RuntimeError:
            # Already set, ignore
            pass
    
    server = AppConnectServer()
    server.run()


if __name__ == "__main__":
    main()

