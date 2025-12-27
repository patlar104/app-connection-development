"""WebSocket server with key exchange and message handling."""
import asyncio
import json
import base64
import logging
import time
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

# Connection health constants
PING_INTERVAL = 30  # seconds - send ping every 30 seconds
PONG_TIMEOUT = 90  # seconds - allow 90 seconds before marking as dead (more lenient for mobile)
HEALTH_CHECK_INTERVAL = 15  # seconds - check health every 15 seconds
INACTIVITY_TIMEOUT = 120  # seconds - mark unhealthy if no activity for 2 minutes


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
        
        # Connection health monitoring
        self._health_check_task: Optional[asyncio.Task] = None
        self._ping_task: Optional[asyncio.Task] = None
        self._running = False
        
        # Connection statistics
        self._stats = {
            "total_connections": 0,
            "active_connections": 0,
            "messages_sent": 0,
            "messages_received": 0,
            "errors": 0,
            "reconnections": 0
        }
        
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
            "address": remote_addr,
            "last_activity": time.time(),
            "last_pong": time.time(),
            "connection_healthy": True,
            "connected_at": time.time(),
            "messages_sent": 0,
            "messages_received": 0
        }
        self.connections[websocket] = connection_state
        self._stats["total_connections"] += 1
        self._stats["active_connections"] = len(self.connections)
        
        try:
            # Wait for key exchange message
            await self._handle_key_exchange(websocket, connection_state)
            
            # Send initial connection status
            await self.send_connection_status(websocket, "connected")
            
            # Handle messages and pings/pongs
            # The websockets library automatically handles pong responses to our pings
            # Any activity (message or successful ping/pong) indicates connection is alive
            async for message in websocket:
                # Update last activity and pong time (any message means connection is alive)
                current_time = time.time()
                connection_state["last_activity"] = current_time
                connection_state["last_pong"] = current_time  # Any activity = connection alive
                connection_state["connection_healthy"] = True
                await self._handle_message(websocket, message, connection_state)
                
        except ConnectionClosed:
            logger.info(f"WebSocket client disconnected: {remote_addr}")
        except Exception as e:
            logger.error(f"Error handling client {remote_addr}: {e}", exc_info=True)
        finally:
            # Cleanup connection
            if websocket in self.connections:
                state = self.connections[websocket]
                uptime = int(time.time() - state.get("connected_at", time.time()))
                logger.info(f"Connection cleanup for {state['address']}: "
                          f"uptime={uptime}s, "
                          f"sent={state.get('messages_sent', 0)}, "
                          f"received={state.get('messages_received', 0)}")
                del self.connections[websocket]
                self._stats["active_connections"] = len(self.connections)
                logger.info(f"Connection cleaned up. Active connections: {self._stats['active_connections']}")
                
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
            
            # Update last activity after successful key exchange
            connection_state["last_activity"] = time.time()
            connection_state["last_pong"] = time.time()
            
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
        """Handle incoming message (encrypted clipboard or plain JSON control messages)."""
        if not connection_state["key_exchanged"]:
            logger.warning("Received message before key exchange")
            return
        
        # Check if this is a control message (plain JSON) or encrypted clipboard data
        # Control messages are plain JSON with a "type" field
        # Encrypted clipboard data is in format "ivBase64|encryptedBase64" (contains pipe, not valid JSON)
        is_control_message = False
        if "|" not in message:
            # No pipe separator - likely a control message (JSON) not encrypted data
            try:
                control_msg = json.loads(message)
                if isinstance(control_msg, dict) and "type" in control_msg:
                    # This is a control message - handle it without decryption
                    await self._handle_control_message(websocket, control_msg, connection_state)
                    return
            except (json.JSONDecodeError, ValueError, TypeError):
                # Not valid JSON - might be encrypted data or malformed message
                logger.debug(f"Message is not valid JSON, treating as encrypted data")
                pass
            except Exception as e:
                logger.debug(f"Error checking if message is control message: {e}")
                # Continue to treat as encrypted clipboard data
                pass
        else:
            # Contains pipe separator - definitely encrypted clipboard data format
            logger.debug("Message contains pipe separator, treating as encrypted clipboard data")
            
        # This is encrypted clipboard data - decrypt and handle
        try:
            encryption = connection_state["encryption"]
            
            # Decrypt message
            decrypted_json = encryption.decrypt_from_transmission(message)
            
            # Parse ClipboardItem
            clipboard_item = ClipboardItem.from_json(decrypted_json)
            
            logger.info(f"Received clipboard: {clipboard_item.content[:50]}...")
            
            # Update statistics
            connection_state["messages_received"] += 1
            self._stats["messages_received"] += 1
            
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
                self._stats["errors"] += 1
                
                # Send error report to client
                try:
                    await self.send_error_report(
                        websocket,
                        "clipboard_write_failed",
                        f"Failed to write clipboard content to PC: {str(e)}",
                        {"exception_type": type(e).__name__}
                    )
                except Exception:
                    pass  # Don't fail if error report fails
                
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
            self._stats["errors"] += 1
            
            # Send error report to client
            try:
                await self.send_error_report(
                    websocket,
                    "decryption_failed",
                    f"Failed to decrypt or parse message: {str(e)}",
                    {"exception_type": type(e).__name__}
                )
            except Exception:
                pass  # Don't fail if error report fails
            
            # Don't re-raise - we want to continue handling messages
    
    async def _handle_control_message(self, websocket: ServerConnection, 
                                     message: Dict, connection_state: Dict) -> None:
        """Handle control messages (error_report, connection_status, clipboard_sync_result)."""
        msg_type = message.get("type", "unknown")
        
        if msg_type == "error_report":
            error_type = message.get("error_type", "unknown")
            error_message = message.get("message", "Unknown error")
            timestamp = message.get("timestamp", 0)
            details = message.get("details", {})
            logger.warning(f"Error report from {connection_state['address']}: [{error_type}] {error_message}")
            if details:
                logger.debug(f"Error details: {details}")
                
        elif msg_type == "connection_status":
            status = message.get("status", "unknown")
            stats = message.get("stats", {})
            logger.info(f"Connection status from {connection_state['address']}: {status}")
            if stats:
                messages_sent = stats.get("messages_sent", 0)
                messages_received = stats.get("messages_received", 0)
                uptime = stats.get("uptime", 0)
                logger.debug(f"Client stats: sent={messages_sent}, received={messages_received}, uptime={uptime}s")
                
        elif msg_type == "clipboard_sync_result":
            success = message.get("success", False)
            clipboard_id = message.get("clipboard_id", "unknown")
            result_message = message.get("message", "")
            if success:
                logger.debug(f"Clipboard sync success from {connection_state['address']}: {clipboard_id} - {result_message}")
            else:
                logger.warning(f"Clipboard sync failed from {connection_state['address']}: {clipboard_id} - {result_message}")
        else:
            logger.debug(f"Unknown control message type from {connection_state['address']}: {msg_type}")
            
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
            
            # Get JSON before encryption for debugging
            json_data = clipboard_item.to_json()
            logger.info(f"ClipboardItem JSON (first 100 chars): {json_data[:100]}...")
            
            # Encrypt and send
            encrypted_message = encryption.encrypt_for_transmission(json_data)
            logger.info(f"Encrypted message length: {len(encrypted_message)}, preview: {encrypted_message[:80]}...")
            
            # Verify we can decrypt our own message (self-test)
            try:
                decrypted_test = encryption.decrypt_from_transmission(encrypted_message)
                if decrypted_test != json_data:
                    logger.error(f"Encryption self-test FAILED: decrypted data doesn't match original!")
                    logger.error(f"Original length: {len(json_data)}, Decrypted length: {len(decrypted_test)}")
                else:
                    logger.info("Encryption self-test PASSED - message can be decrypted correctly")
            except Exception as e:
                logger.error(f"Encryption self-test FAILED with exception: {e}", exc_info=True)
            
            # Send the message
            try:
                await websocket.send(encrypted_message)
                logger.info(f"Sent clipboard: {content[:50]}...")
                logger.info(f"Full encrypted message format check - has pipe: {'|' in encrypted_message}, parts: {len(encrypted_message.split('|'))}")
                
                # Update statistics
                connection_state["messages_sent"] += 1
                connection_state["last_activity"] = time.time()
                self._stats["messages_sent"] += 1
                
                return True
            except Exception as send_error:
                logger.error(f"Failed to send WebSocket message: {send_error}", exc_info=True)
                self._stats["errors"] += 1
                connection_state["connection_healthy"] = False
                
                # Send error report to client
                try:
                    await self.send_error_report(
                        websocket,
                        "send_failed",
                        f"Failed to send clipboard message: {str(send_error)}",
                        {"exception_type": type(send_error).__name__}
                    )
                except Exception:
                    pass  # Don't fail if error report fails
                
                return False
            
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
    
    def get_connection_stats(self) -> Dict:
        """Get connection statistics."""
        return self._stats.copy()
    
    async def send_error_report(self, websocket: ServerConnection, error_type: str, 
                                message: str, details: Optional[Dict] = None) -> bool:
        """
        Send error report to client.
        
        Args:
            websocket: WebSocket connection
            error_type: Type of error (decryption_failed, send_failed, connection_lost, etc.)
            message: Human-readable error message
            details: Optional additional error details
            
        Returns:
            True if sent successfully, False otherwise
        """
        if websocket not in self.connections:
            return False
        
        try:
            error_report = {
                "type": "error_report",
                "error_type": error_type,
                "message": message,
                "timestamp": int(time.time()),
                "details": details or {}
            }
            
            await websocket.send(json.dumps(error_report))
            logger.info(f"Sent error report to {self.connections[websocket]['address']}: {error_type} - {message}")
            return True
        except Exception as e:
            logger.error(f"Failed to send error report: {e}", exc_info=True)
            return False
    
    async def send_connection_status(self, websocket: ServerConnection, status: str) -> bool:
        """
        Send connection status update to client.
        
        Args:
            websocket: WebSocket connection
            status: Status message (connected, healthy, unhealthy, etc.)
            
        Returns:
            True if sent successfully, False otherwise
        """
        if websocket not in self.connections:
            return False
        
        try:
            state = self.connections[websocket]
            status_report = {
                "type": "connection_status",
                "status": status,
                "timestamp": int(time.time()),
                "stats": {
                    "messages_sent": state.get("messages_sent", 0),
                    "messages_received": state.get("messages_received", 0),
                    "uptime": int(time.time() - state.get("connected_at", time.time()))
                }
            }
            
            await websocket.send(json.dumps(status_report))
            logger.debug(f"Sent connection status to {state['address']}: {status}")
            return True
        except Exception as e:
            logger.error(f"Failed to send connection status: {e}", exc_info=True)
            return False
    
    async def _ping_connections(self) -> None:
        """Send ping frames to all connected clients periodically."""
        while self._running:
            try:
                await asyncio.sleep(PING_INTERVAL)
                
                if not self._running:
                    break
                
                current_time = time.time()
                dead_connections = []
                
                for websocket, state in list(self.connections.items()):
                    try:
                        # Check if connection hasn't responded to pings (more lenient timeout)
                        time_since_pong = current_time - state["last_pong"]
                        time_since_activity = current_time - state["last_activity"]
                        
                        # Only mark as dead if both pong and activity are stale
                        # This is more lenient for mobile networks with temporary interruptions
                        if time_since_pong > PONG_TIMEOUT and time_since_activity > PONG_TIMEOUT:
                            logger.warning(f"Connection {state['address']} is stale: "
                                        f"last_pong={time_since_pong:.1f}s ago, "
                                        f"last_activity={time_since_activity:.1f}s ago - marking as dead")
                            state["connection_healthy"] = False
                            dead_connections.append(websocket)
                            continue
                        
                        # Send ping frame (websockets library handles pong automatically)
                        # The websockets library will automatically handle pong responses
                        # We update last_pong on any activity (messages, etc.)
                        await websocket.ping()
                        logger.debug(f"Sent ping to {state['address']} (last_pong: {time_since_pong:.1f}s ago, "
                                   f"last_activity: {time_since_activity:.1f}s ago)")
                    except (ConnectionClosed, Exception) as e:
                        logger.warning(f"Failed to ping {state['address']}: {e}")
                        dead_connections.append(websocket)
                
                # Remove dead connections
                for ws in dead_connections:
                    if ws in self.connections:
                        try:
                            await ws.close()
                        except Exception:
                            pass
                        del self.connections[ws]
                        self._stats["active_connections"] = len(self.connections)
                        logger.info(f"Removed dead connection. Active connections: {self._stats['active_connections']}")
                        
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in ping task: {e}", exc_info=True)
                await asyncio.sleep(PING_INTERVAL)
    
    async def _health_check(self) -> None:
        """Periodic health check for all connections."""
        while self._running:
            try:
                await asyncio.sleep(HEALTH_CHECK_INTERVAL)
                
                if not self._running:
                    break
                
                current_time = time.time()
                unhealthy_connections = []
                
                for websocket, state in list(self.connections.items()):
                    # Check connection health
                    time_since_activity = current_time - state["last_activity"]
                    time_since_pong = current_time - state["last_pong"]
                    
                    # Mark as unhealthy if no activity for too long (more lenient for mobile networks)
                    # Only mark unhealthy, don't disconnect - give it time to recover
                    was_healthy = state["connection_healthy"]
                    
                    # Use more lenient thresholds - require both to be stale before marking unhealthy
                    # This prevents false positives from temporary network hiccups
                    pong_stale = time_since_pong > PONG_TIMEOUT
                    activity_stale = time_since_activity > INACTIVITY_TIMEOUT
                    
                    if pong_stale and activity_stale:
                        # Both are stale - mark as unhealthy but don't disconnect yet
                        state["connection_healthy"] = False
                        if was_healthy:
                            # Only log when transitioning from healthy to unhealthy
                            logger.warning(f"Connection {state['address']} marked unhealthy: "
                                         f"last_activity={time_since_activity:.1f}s ago, "
                                         f"last_pong={time_since_pong:.1f}s ago")
                        unhealthy_connections.append((websocket, state))
                    elif not was_healthy and not pong_stale and not activity_stale:
                        # Connection recovered - mark as healthy again
                        state["connection_healthy"] = True
                        logger.info(f"Connection {state['address']} recovered and marked healthy "
                                  f"(last_activity={time_since_activity:.1f}s ago, "
                                  f"last_pong={time_since_pong:.1f}s ago)")
                    elif pong_stale or activity_stale:
                        # One is stale but not both - log but don't mark unhealthy yet
                        # This gives mobile networks more grace period
                        logger.debug(f"Connection {state['address']} showing signs of latency: "
                                   f"last_activity={time_since_activity:.1f}s ago, "
                                   f"last_pong={time_since_pong:.1f}s ago (still monitoring)")
                
                if unhealthy_connections:
                    logger.warning(f"Found {len(unhealthy_connections)} unhealthy connections")
                    for ws, state in unhealthy_connections:
                        logger.warning(f"Unhealthy connection: {state['address']}, "
                                     f"last_activity: {current_time - state['last_activity']:.1f}s ago, "
                                     f"last_pong: {current_time - state['last_pong']:.1f}s ago")
                
                # Log connection statistics periodically
                if self.connections:
                    healthy_count = sum(1 for s in self.connections.values() if s['connection_healthy'])
                    logger.info(f"Connection health: {len(self.connections)} active, {healthy_count} healthy")
                    
                    # Log detailed stats for each connection
                    for ws, state in self.connections.items():
                        uptime = int(current_time - state.get("connected_at", current_time))
                        logger.debug(f"  {state['address']}: "
                                   f"uptime={uptime}s, "
                                   f"sent={state.get('messages_sent', 0)}, "
                                   f"received={state.get('messages_received', 0)}, "
                                   f"healthy={state['connection_healthy']}")
                    
            except asyncio.CancelledError:
                break
            except Exception as e:
                logger.error(f"Error in health check task: {e}", exc_info=True)
                await asyncio.sleep(HEALTH_CHECK_INTERVAL)
    
    async def start(self, host: str = "0.0.0.0", port: int = 8765) -> None:
        """Start WebSocket server."""
        logger.info(f"Starting WebSocket server on {host}:{port}")
        
        self._running = True
        
        # Start health monitoring tasks
        self._ping_task = asyncio.create_task(self._ping_connections())
        self._health_check_task = asyncio.create_task(self._health_check())
        
        async with websockets.serve(
            self.handle_client,
            host,
            port,
            ssl=self.ssl_context,
            ping_interval=PING_INTERVAL,
            ping_timeout=PONG_TIMEOUT
        ) as server:
            logger.info(f"WebSocket server running on wss://{host}:{port}")
            
            # Keep server running
            try:
                await asyncio.Future()  # Run forever
            finally:
                self._running = False
                if self._ping_task:
                    self._ping_task.cancel()
                    try:
                        await self._ping_task
                    except asyncio.CancelledError:
                        pass
                if self._health_check_task:
                    self._health_check_task.cancel()
                    try:
                        await self._health_check_task
                    except asyncio.CancelledError:
                        pass

