# Architecture

This document provides a comprehensive overview of the AppConnect architecture.

## Overview

AppConnect follows Clean Architecture principles with MVVM pattern for the presentation layer. The architecture is designed to be modular, testable, and maintainable.

```
┌─────────────────────────────────────────────────────────────────┐
│                      Presentation Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│  │   Compose    │──│  ViewModels  │──│  Navigation Graph   │  │
│  │     UI       │  │              │  │                     │  │
│  └──────────────┘  └──────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Domain Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│  │    Models    │  │  Use Cases   │  │   Repositories      │  │
│  │              │  │  (Business)  │  │   (Interfaces)      │  │
│  └──────────────┘  └──────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                         Data Layer                              │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│  │     Room     │  │ Repositories │  │      Mappers        │  │
│  │   Database   │  │              │  │                     │  │
│  └──────────────┘  └──────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                      Infrastructure                             │
│  ┌──────────────┐  ┌──────────────┐  ┌─────────────────────┐  │
│  │   Network    │  │  Encryption  │  │     Services        │  │
│  │  (WS + BT)   │  │   Manager    │  │   (Foreground)      │  │
│  └──────────────┘  └──────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Layers

### 1. Presentation Layer (`presentation/`)

**Responsibility**: UI and user interaction

**Components**:
- **Jetpack Compose UI**: Modern declarative UI
- **ViewModels**: Manage UI state and business logic coordination
- **Theme**: Material 3 theming

**Pattern**: MVVM (Model-View-ViewModel)

**Dependencies**: Domain layer only

### 2. Domain Layer (`domain/`)

**Responsibility**: Business logic and entities

**Components**:
- **Models**: Core business entities (`ClipboardItem`, `Device`, `QrConnectionInfo`)
- **Repository Interfaces**: Abstract data access

**Pattern**: Clean Architecture domain layer

**Dependencies**: None (pure Kotlin)

### 3. Data Layer (`data/`)

**Responsibility**: Data management and persistence

**Components**:
- **Room Database**: Local SQLite database
- **DAOs**: Data Access Objects for database operations
- **Entities**: Database table representations
- **Repositories**: Implementation of domain repository interfaces
- **Mappers**: Convert between domain models and entities

**Pattern**: Repository pattern

**Dependencies**: Domain layer

### 4. Core Layer (`core/`)

**Responsibility**: Cross-cutting concerns and business services

**Components**:
- **SyncManager**: Orchestrates clipboard synchronization
- **EncryptionManager**: Handles encryption/decryption with Android Keystore
- **PairingManager**: Manages device pairing
- **HealthManager**: App health monitoring
- **NotificationManager**: Notification creation and management
- **CrashReporter**: Error reporting

**Dependencies**: Domain, Data layers

### 5. Network Layer (`network/`)

**Responsibility**: Communication with external devices

**Components**:
- **WebSocketClient**: Primary transport (TLS/SSL)
- **BluetoothManager**: Fallback transport
- **NsdHelper**: Network service discovery (mDNS)
- **CompanionDeviceManagerHelper**: CDM integration
- **PairedDeviceTrustManager**: Certificate pinning

**Pattern**: Strategy pattern for transport selection

**Dependencies**: Data layer

### 6. Service Layer (`service/`)

**Responsibility**: Android system services

**Components**:
- **ClipboardSyncService**: Foreground service for persistent connection
- **ClipboardAccessibilityService**: Optional accessibility service
- **ClipboardSyncTile**: Quick Settings tile
- **BootCompletedReceiver**: Auto-start on boot
- **CopyActionReceiver**: Notification action handler

**Dependencies**: Core, Network, Data layers

### 7. DI Layer (`di/`)

**Responsibility**: Dependency injection configuration

**Components**:
- **AppModule**: Application-level dependencies
- **DatabaseModule**: Room database provision
- **NetworkModule**: Network components
- **RepositoryModule**: Repository implementations

**Framework**: Hilt/Dagger

## Data Flow

### Outgoing Clipboard Sync

```
User Copies Text
       │
       ▼
ClipboardManager Listener (Service)
       │
       ▼
SyncManager.syncClipboard()
       │
       ├─→ Repository.saveClipboardItem() → Room DB
       │
       └─→ EncryptionManager.encrypt()
              │
              ▼
       Transport Selection (WebSocket/Bluetooth)
              │
              ▼
       Send to Paired Device
```

### Incoming Clipboard Sync

```
Receive Encrypted Data (WebSocket/Bluetooth)
       │
       ▼
SyncManager.handleIncomingClipboard()
       │
       ├─→ EncryptionManager.decrypt()
       │
       ├─→ Repository.saveClipboardItem() → Room DB
       │
       └─→ App State Check
              │
              ├─→ Foreground: Direct clipboard write
              └─→ Background: Show notification with copy action
```

## Security Architecture

### Encryption Layer

- **Algorithm**: AES-256-GCM
- **Key Storage**: Android Keystore
- **IV Generation**: Random per encryption operation
- **Data Flow**: All clipboard content encrypted at rest and in transit

### Certificate Pinning

- QR code contains certificate fingerprint
- TrustManager validates against pinned certificate
- Prevents MITM attacks during pairing

### Data Protection

- Database: Encrypted via Android Keystore
- Network: TLS/SSL for WebSocket
- Backup: Excluded sensitive data via backup rules
- No cloud storage

## Transport Strategy

### Primary: WebSocket (TLS)

- **Advantages**: Fast, bi-directional, supports large data (images)
- **Port**: 8765 (configurable)
- **Discovery**: mDNS (`_appconnect._tcp`)

### Fallback: Bluetooth

- **Advantages**: Works without Wi-Fi
- **Limitations**: Text only (no images), slower
- **Pairing**: Bluetooth Classic or BLE

### Selection Logic

1. Attempt WebSocket connection
2. On failure, fall back to Bluetooth
3. Image sync requires WebSocket

## Background Operation Strategy

### Companion Device Manager (CDM)

- Grants background permissions
- Required for Android 12+
- Associates app with paired device

### Foreground Service

- Type: `connectedDevice`
- Maintains persistent connection
- Shows notification while active

### Accessibility Service (Optional)

- Full clipboard access in background
- User must explicitly enable
- Alternative to logcat monitoring

## Testing Strategy

### Unit Tests

- ViewModels: State management and business logic
- Repositories: Data operations
- Managers: Core business services
- Use MockK for mocking

### Instrumented Tests

- DAOs: Database operations
- Encryption: Android Keystore integration
- Services: Android component lifecycle

### Integration Tests

- End-to-end clipboard sync flow
- Transport fallback mechanism
- Pairing process

## Build Variants

### Debug

- No code shrinking
- Debug logging enabled
- Network security allows local testing

### Release

- R8 code shrinking enabled
- ProGuard optimization
- Debug logging disabled
- Certificate pinning enforced

## Performance Considerations

### Database

- TTL-based cleanup via WorkManager
- Indexed queries for performance
- Flow-based reactive updates

### Network

- Connection pooling (OkHttp)
- Efficient WebSocket frame handling
- Debounced notifications (500ms)

### Memory

- Lifecycle-aware ViewModels
- Proper coroutine scope management
- Resource cleanup in onDestroy()

## Future Enhancements

- Multi-device support
- File transfer support
- Cross-platform PC apps (Windows, macOS, Linux)
- End-to-end encrypted group sync
- Conflict resolution for simultaneous edits
