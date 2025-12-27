"""WebSocket server with key exchange and message handling."""
import asyncio
import json
import base64
import logging
from pathlib import Path
from typing import Optional, Dict, Callable
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives import hashes
import websockets
from websockets.asyncio.server import ServerConnection
from websockets.exceptions import ConnectionClosed

from .encryption import EncryptionManager
from .message_parser import ClipboardItem
from .clipboard_writer import ClipboardWriter

logger = logging.getLogger(__name__)


class WebSocketServer:
    """Secure WebSocket server for clipboard synchronization."""
    
    def __init__(self, ssl_context, rsa_private_key_file: Path, 
                 device_id: str, on_clipboard_received: Optional[Callable[[str], None]] = None,
                 pre_shared_key: Optional[bytes] = None):
        """
        Initialize WebSocket server.
        
        Args:
            ssl_context: SSL context for WSS
            rsa_private_key_file: Path to RSA private key for key exchange
            device_id: Device identifier for sourceDeviceId
            on_clipboard_received: Optional callback when clipboard is received from Android
            pre_shared_key: Optional pre-shared AES key (for testing without key exchange)
        """
        self.ssl_context = ssl_context
        self.rsa_private_key_file = rsa_private_key_file
        self.device_id = device_id
        self.on_clipboard_received = on_clipboard_received
        self.pre_shared_key = pre_shared_key
        
        # Per-connection state
        self.connections: Dict[ServerConnection, Dict] = {}
        
        # Load RSA private key (only if not using pre-shared key)
        self._rsa_private_key = None
        if not pre_shared_key:
            self._load_rsa_key()
        
    def _load_rsa_key(self) -> None:
        """Load RSA private key for key exchange."""
        if not self.rsa_private_key_file.exists():
            raise FileNotFoundError(
                f"RSA private key file not found: {self.rsa_private_key_file}. "
                f"Please ensure the key file exists or run the server to generate it."
            )
        
        try:
            with open(self.rsa_private_key_file, "rb") as f:
                key_data = f.read()
                if not key_data:
                    raise ValueError(f"RSA private key file is empty: {self.rsa_private_key_file}")
                
                self._rsa_private_key = serialization.load_pem_private_key(
                    key_data,
                    password=None
                )
                logger.info(f"Loaded RSA private key from {self.rsa_private_key_file}")
        except FileNotFoundError:
            raise
        except Exception as e:
            logger.error(f"Failed to load RSA private key from {self.rsa_private_key_file}: {e}")
            raise ValueError(f"Invalid RSA private key file: {e}") from e
            
    async def handle_client(self, websocket: ServerConnection) -> None:
        """Handle a WebSocket client connection."""
        remote_addr = websocket.remote_address
        logger.info(f"WebSocket client connected: {remote_addr}")
        
        # Initialize connection state
        connection_state = {
            "encryption": None,  # EncryptionManager instance
            "key_exchanged": False,
            "address": remote_addr
        }
        self.connections[websocket] = connection_state
        
        try:
            # Wait for key exchange message
            await self._handle_key_exchange(websocket, connection_state)
            
            # Handle messages
            async for message in websocket:
                await self._handle_message(websocket, message, connection_state)
                
        except ConnectionClosed:
            logger.info(f"WebSocket client disconnected: {remote_addr}")
        except Exception as e:
            logger.error(f"Error handling client {remote_addr}: {e}", exc_info=True)
        finally:
            # Cleanup
            if websocket in self.connections:
                del self.connections[websocket]
                
    async def _handle_key_exchange(self, websocket: ServerConnection, 
                                   connection_state: Dict) -> None:
        """Handle RSA-based key exchange or use pre-shared key."""
        # If pre-shared key is set, use it directly
        if self.pre_shared_key:
            encryption = EncryptionManager(self.pre_shared_key)
            connection_state["encryption"] = encryption
            connection_state["key_exchanged"] = True
            logger.info(f"Using pre-shared key for {connection_state['address']}")
            return
            
        # Otherwise, perform RSA key exchange
        try:
            # Wait for encrypted AES key from client
            message = await asyncio.wait_for(websocket.recv(), timeout=10.0)
            
            # Parse key exchange message
            # Format: {"type": "key_exchange", "encrypted_key": "base64_encrypted_aes_key"}
            key_exchange_data = json.loads(message)
            
            if key_exchange_data.get("type") != "key_exchange":
                raise ValueError("Expected key exchange message")
                
            encrypted_key_b64 = key_exchange_data.get("encrypted_key")
            if not encrypted_key_b64:
                raise ValueError("Missing encrypted_key in key exchange")
            
            # Add padding for Base64 decode (Android uses NO_WRAP which strips padding)
            def add_padding(s: str) -> str:
                missing_padding = len(s) % 4
                if missing_padding:
                    return s + '=' * (4 - missing_padding)
                return s
                
            # Decode and decrypt AES key
            logger.debug(f"Received encrypted key (length: {len(encrypted_key_b64)})")
            encrypted_key = base64.b64decode(add_padding(encrypted_key_b64))
            logger.debug(f"Decoded encrypted key (length: {len(encrypted_key)} bytes)")
            aes_key = self._rsa_private_key.decrypt(
                encrypted_key,
                padding.OAEP(
                    mgf=padding.MGF1(algorithm=hashes.SHA256()),
                    algorithm=hashes.SHA256(),
                    label=None
                )
            )
            
            # Validate decrypted AES key size (must be 32 bytes for AES-256)
            if len(aes_key) != 32:
                raise ValueError(f"Invalid AES key size: {len(aes_key)} bytes (expected 32)")
            
            # Initialize encryption manager with shared key
            encryption = EncryptionManager(aes_key)
            connection_state["encryption"] = encryption
            connection_state["key_exchanged"] = True
            
            # Send confirmation
            await websocket.send(json.dumps({"type": "key_exchange_ack", "status": "ok"}))
            
            logger.info(f"Key exchange completed for {connection_state['address']}")
            
        except asyncio.TimeoutError:
            logger.warning("Key exchange timeout")
            raise
        except Exception as e:
            logger.error(f"Key exchange failed: {e}", exc_info=True)
            # Try to send error response to client before closing connection
            try:
                await websocket.send(json.dumps({
                    "type": "key_exchange_ack", 
                    "status": "error", 
                    "message": str(e)
                }))
            except Exception:
                # Connection may already be closed, ignore
                pass
            # Re-raise to close the connection (this is expected behavior on error)
            raise
            
    async def _handle_message(self, websocket: ServerConnection, 
                             message: str, connection_state: Dict) -> None:
        """Handle incoming encrypted message."""
        if not connection_state["key_exchanged"]:
            logger.warning("Received message before key exchange")
            return
            
        try:
            encryption = connection_state["encryption"]
            
            # Decrypt message
            decrypted_json = encryption.decrypt_from_transmission(message)
            
            # Parse ClipboardItem
            clipboard_item = ClipboardItem.from_json(decrypted_json)
            
            logger.info(f"Received clipboard: {clipboard_item.content[:50]}...")
            
            # Write to clipboard in executor to avoid blocking the event loop
            # This prevents clipboard operations from causing connection timeouts
            loop = asyncio.get_event_loop()
            try:
                await loop.run_in_executor(
                    None,
                    ClipboardWriter.write_text,
                    clipboard_item.content
                )
                logger.debug("Successfully wrote to clipboard")
            except Exception as e:
                logger.error(f"Error writing to clipboard: {e}", exc_info=True)
                # Continue even if clipboard write fails
            
            # Notify callback if set (callback is synchronous, run in executor to avoid blocking)
            if self.on_clipboard_received:
                try:
                    # Run callback in executor to avoid blocking the event loop
                    await loop.run_in_executor(
                        None,
                        lambda: self.on_clipboard_received(clipboard_item.content)
                    )
                except Exception as e:
                    logger.error(f"Error in clipboard received callback: {e}", exc_info=True)
                    # Continue even if callback fails
                
        except Exception as e:
            logger.error(f"Error handling message: {e}", exc_info=True)
            # Don't re-raise - we want to continue handling messages
            
    async def send_clipboard(self, websocket: ServerConnection, 
                            content: str) -> bool:
        """
        Send clipboard content to connected client.
        
        Args:
            websocket: WebSocket connection
            content: Clipboard text content
            
        Returns:
            True if sent successfully, False otherwise
        """
        if websocket not in self.connections:
            return False
            
        connection_state = self.connections[websocket]
        
        if not connection_state["key_exchanged"]:
            logger.warning("Cannot send: key exchange not completed")
            return False
            
        try:
            encryption = connection_state["encryption"]
            
            # Create ClipboardItem
            clipboard_item = ClipboardItem.create_text_item(
                content=content,
                source_device_id=self.device_id
            )
            
            # Encrypt and send
            encrypted_message = encryption.encrypt_for_transmission(clipboard_item.to_json())
            await websocket.send(encrypted_message)
            
            logger.info(f"Sent clipboard: {content[:50]}...")
            return True
            
        except Exception as e:
            logger.error(f"Error sending clipboard: {e}", exc_info=True)
            return False
            
    async def broadcast_clipboard(self, content: str) -> int:
        """
        Broadcast clipboard content to all connected clients.
        
        Returns:
            Number of clients that received the message
        """
        count = 0
        disconnected = []
        
        for websocket in list(self.connections.keys()):
            try:
                if await self.send_clipboard(websocket, content):
                    count += 1
            except Exception:
                disconnected.append(websocket)
                
        # Clean up disconnected clients
        for ws in disconnected:
            if ws in self.connections:
                del self.connections[ws]
                
        return count
        
    def get_connection_count(self) -> int:
        """Get number of connected clients."""
        return len(self.connections)
        
    async def start(self, host: str = "0.0.0.0", port: int = 8765) -> None:
        """Start WebSocket server."""
        logger.info(f"Starting WebSocket server on {host}:{port}")
        
        async with websockets.serve(
            self.handle_client,
            host,
            port,
            ssl=self.ssl_context
        ):
            logger.info(f"WebSocket server running on wss://{host}:{port}")
            # Keep server running
            await asyncio.Future()  # Run forever

