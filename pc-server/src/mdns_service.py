"""mDNS service broadcasting for device discovery."""
from zeroconf import ServiceInfo
from zeroconf.asyncio import AsyncZeroconf
from typing import Optional
import socket
import asyncio


class MDNSService:
    """Manages mDNS service broadcasting."""
    
    def __init__(self, service_type: str = "_appconnect._tcp", 
                 app_id: str = "dev.appconnect"):
        self.service_type = service_type
        self.app_id = app_id
        self.zeroconf: Optional[AsyncZeroconf] = None
        self.service_info: Optional[ServiceInfo] = None
        self._event_loop: Optional[asyncio.AbstractEventLoop] = None
            
    async def start_async(self, name: str, port: int, ip: Optional[str] = None) -> None:
        """Start broadcasting mDNS service (async)."""
        if self.zeroconf:
            await self.stop_async()
            
        # Get local IP if not provided
        if not ip:
            ip = self._get_local_ip()
            
        if not ip:
            print("Warning: Could not determine local IP for mDNS")
            return
            
        # Store event loop reference
        self._event_loop = asyncio.get_running_loop()
            
        # Create AsyncZeroconf instance
        self.zeroconf = AsyncZeroconf()
        
        # Create service info
        # Service name format: {instance}.{service_type}.local.
        service_name = f"{name}.{self.service_type}.local."
        self.service_info = ServiceInfo(
            type_=f"{self.service_type}.local.",
            name=service_name,
            addresses=[socket.inet_aton(ip)],
            port=port,
            properties={"app_id": self.app_id},
            server=f"{name}.local."
        )
        
        # Register service asynchronously
        try:
            await self.zeroconf.async_register_service(self.service_info)
            print(f"mDNS service broadcasting: {name}.{self.service_type} on {ip}:{port}")
        except Exception as e:
            print(f"Warning: mDNS registration failed: {e}")
        
    async def stop_async(self) -> None:
        """Stop broadcasting mDNS service (async)."""
        if self.zeroconf and self.service_info:
            try:
                await self.zeroconf.async_unregister_service(self.service_info)
            except Exception:
                pass  # Ignore errors on shutdown
                
            try:
                # Give zeroconf a moment to finish any pending operations
                await asyncio.sleep(0.1)
                await self.zeroconf.async_close()
                # Wait a bit more for cleanup tasks
                await asyncio.sleep(0.1)
            except Exception:
                pass
                
            self.zeroconf = None
            self.service_info = None
            self._event_loop = None
            
            print("mDNS service stopped")
            
    def _get_local_ip(self) -> Optional[str]:
        """Get local IP address."""
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ip = s.getsockname()[0]
            s.close()
            return ip
        except Exception:
            return None

