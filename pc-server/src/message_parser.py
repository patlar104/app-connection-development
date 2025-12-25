"""Message parsing for ClipboardItem JSON format."""
import json
import uuid
from typing import Dict, Any, Optional
from datetime import datetime, timedelta


class ClipboardItem:
    """Represents a clipboard item matching Android ClipboardItem model."""
    
    def __init__(self, id: str, content: str, contentType: str, 
                 timestamp: int, ttl: int, synced: bool,
                 sourceDeviceId: Optional[str], hash: str):
        self.id = id
        self.content = content
        self.contentType = contentType  # TEXT, IMAGE, or FILE
        self.timestamp = timestamp
        self.ttl = ttl
        self.synced = synced
        self.sourceDeviceId = sourceDeviceId
        self.hash = hash
        
    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary for JSON serialization."""
        return {
            "id": self.id,
            "content": self.content,
            "contentType": self.contentType,
            "timestamp": self.timestamp,
            "ttl": self.ttl,
            "synced": self.synced,
            "sourceDeviceId": self.sourceDeviceId,
            "hash": self.hash
        }
        
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'ClipboardItem':
        """Create ClipboardItem from dictionary."""
        return cls(
            id=data["id"],
            content=data["content"],
            contentType=data["contentType"],
            timestamp=data["timestamp"],
            ttl=data["ttl"],
            synced=data["synced"],
            sourceDeviceId=data.get("sourceDeviceId"),
            hash=data["hash"]
        )
        
    @classmethod
    def create_text_item(cls, content: str, source_device_id: Optional[str] = None) -> 'ClipboardItem':
        """
        Create a TEXT clipboard item from content.
        
        Args:
            content: Clipboard text content
            source_device_id: Optional source device identifier
        """
        from .encryption import EncryptionManager
        
        # Calculate hash (lowercase hex)
        hash_value = EncryptionManager.calculate_hash(content)
        
        # Calculate TTL (24 hours in milliseconds)
        ttl_ms = 24 * 60 * 60 * 1000
        
        return cls(
            id=str(uuid.uuid4()),
            content=content,
            contentType="TEXT",
            timestamp=int(datetime.utcnow().timestamp() * 1000),
            ttl=ttl_ms,
            synced=False,
            sourceDeviceId=source_device_id,
            hash=hash_value
        )
        
    def to_json(self) -> str:
        """Serialize to JSON string."""
        return json.dumps(self.to_dict(), separators=(',', ':'))
        
    @classmethod
    def from_json(cls, json_str: str) -> 'ClipboardItem':
        """Deserialize from JSON string."""
        data = json.loads(json_str)
        return cls.from_dict(data)

