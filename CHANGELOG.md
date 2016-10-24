# Change Log

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
