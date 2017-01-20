# Change Log

## [1.2.1] - 2017-01-20

### Android

#### Fixed

- Use the correct javadocs deprecated tag
- Mark `opInProgress` as false when a queue request fails to prevent unresponsive operations after a reconnection.

#### Changed

- Improve `RigDfuError` documentation


## [1.2] - 2016-12-30

### iOS

#### Fixed

- Correct the error code for CRC validation
- Reset Device Delegate correctly under some DFU failure cases

#### Changed

- Support cancellation of firmware updates
- Add optional Descriptor delegate methods to protocol

### Android

#### Fixed

- Fix progress value for patched updates
- Prevent NPEs caused by forced disconnects
- Prevent NPE caused by invalid binary; pass back error code INVALID_PARAMETER
- Add null check for observers in some circumstances
- Synchronize methods that modify the gatt hashmap & clear queue operation
- Update targeted API handling to prevent linter error
- Fix and deprecate RigService method to retrieve connection state (use RigCoreBluetooth instead)
- Set firmware service at start of DFU to prevent NPEs on update failures
- Write the correct notification toggle value to the descriptor

#### Changed

- Support cancellation of firmware updates
- Add ability to cancel connection requests
- Add ability to read ble descriptors
- Mark `opInProgress` as false when queue is cleared; prevents unresponsive operations after a reconnection.
- Stop device discovery on cancel
- Reset discovery flag on initialization
- Parse name from raw scan record bytes
- Return RigDfu disconnect failure during forced disconnects
- Handle missing device and characteristic errors.
- Log error when discovery observer is null


## [1.1.1] - 2016-09-23

### Android

#### Changed
- Use newer BLE interfaces when available (API 21+)
- Fix DFU activation when write confirmation and validation happen out-of-order
- Better handle DFU disconnect failures
- Fail DFU gracefully when connection fails
- Fix occasional issue with connection timeout firing unexpectedly

### iOS

#### Changed
- Improve reliability of DFU device discovery
- Correctly reset state before DFU reconnection
- Fix discovery timeout callback when fired on background thread
- Use appropriate write (response) type in DFU
- Add respondsToSelector check for optional updateFailed callback

## [1.1.0] - 2016-08-19

### Android

#### Added
- DFU Support for BMD-300 series
- Added `RigCoreBluetooth.initialize` as an optional single entry point
- Support for patched updating
- Updated error handling and introduced RigDfuError
- Support for Android Studio

### iOS

#### Added
- DFU Support for BMD-300 series

#### Changed
- Change some return types to instancetype, for Swift type inference (halmueller) [#11]
- Fix Typo in RigLe method getServiceList (halmueller) [#11]
- Patch and size parameters have been removed from RigFirmwareUpdateManager's updateFirmware method, now: `updateFirmware:image:activateChar:activateCommand:activateCommandLen:`

## 1.0.0

Initial Version

# Contributors

- @siarheimilkevich
- @pgerlach
- @halmueller
- @talarari
