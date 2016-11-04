## RigaBlue iOS

### Overview

Rigablue is an easy to use library for Bluetooth Low Energy (BLE) device integration. Rigablue abstracts away many of the low level details required for typical BLE operations: scanning, connecting, and communications.

### Include the Rigablue Source in your App

One way to work with the Rigablue source is to add its `.xcodeproj` and your own project to a common workspace. We'll assume you have copied this SDK to a working directory.

* In Xcode, create a new workspace (File > New > Workspace...) and save it
* With your new workspace open, add the Rigablue project (`Rigablue.xcodeproj`) to the workspace (File > Add Files to "MyWorkspace"...)
* Add your own project to the same workspace
    - If you are creating a new project, you can add it to your workspace when you initially create it
* Link the SDK: In your target's Build Phases, add an entry under "Link Binary with Libraries" and select libRigablue.

## Using Rigablue

To use Rigablue, import the library as needed.

- *For Objective-C*, `#import "Rigablue/Rigablue.h"`
- *For Swift*, create a bridging header file
    + File > New > File... > iOS / Source / Header File
    + In the bridging header, add `#import "Rigablue/Rigablue.h"`
    + Add the path to your bridging header in Build Settings ("Swift Compiler - Code Generation > Objective-C Bridging Header")

Depending on where you've placed the SDK and your app files, you may need to add the Rigablue header files to your Header Search Paths in your Build Settings.

## Setting up BLE Discovery:

### Overview

The `RigLeDiscoveryManager` singleton provides an interface for peripheral discovery. You should begin by calling `startLeInterface`.

To start discovery of devices, you will need to create a `RigDeviceRequest` and pass it to a `RigLeDiscoveryManager` in the `discoverDevices` method. The device request may contain an array of UUIDs that you want to filter for. Your delegate must adhere to the `RigLeDiscoveryManagerDelegate` protocol, and will be notified of updates.

### Examples

The following examples in Swift and Objective-C provide a simple starting point.

**Swift:**
```swift
let discoveryManager = RigLeDiscoveryManager.sharedInstance()
// let deviceUUID : CBUUID = ...
let uuidList = [deviceUUID]
let request = RigDeviceRequest.deviceRequestWithUuidList(uuidList, timeout: 10, delegate: self, allowDuplicates: true) as? RigDeviceRequest
discoveryManager.startLeInterface()
discoveryManager.discoverDevices(request)
```

**Objective-C:**
```objective-c
RigLeDiscoveryManager *discoveryManager = [RigLeDiscoveryManager sharedInstance];
[discoveryManager startLeInterface];
// Here, we won't filter for any specific UUIDs, so any advertising devices will be seen
RigDeviceRequest *request = [RigDeviceRequest deviceRequestWithUuidList:nil
                                                                timeout:10
                                                                delegate:self
                                                                allowDuplicates:YES];
[discoveryManager discoverDevices:request];
```

Similarly, to connect to a device, you'll work with the `RigLeConnectionManager` and conform to the `RigLeConnectionManagerDelegate` protocol. Once your discovery delegate has discovered an advertising device, you can connect to it by calling `connectDevice`.

**Swift:**
```swift
let connectionManager = RigLeConnectionManager.sharedInstance()
connectionManager.delegate = self
// We have an instance of RigAvailableDeviceData called deviceData
connectionManager.connectDevice(deviceData, connectionTimeout: 10)
```

**Objective-C:**
```objective-c
RigLeConnectionManager *connectionManager = [RigLeConnectionManager sharedInstance];
connectionManager.delegate = self;
// We have an instance of RigAvailableDeviceData called deviceData
[connectionManager connectDevice:deviceData connectionTimeout:10];
```

For more complete examples, see the `rigado/bmd-eval-examples` repository.
