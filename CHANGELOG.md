# Change Log

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
