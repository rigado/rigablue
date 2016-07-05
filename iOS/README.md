# RigaBlue iOS
There are a few ways to incorporate the RigaBlue SDK into your iOS project. You may include a framework, or add the source files to your project.

This will walk you through the steps to add the source SDK to a new app.

## Including the SDK in a new app:

In Xcode, create or open a new Single View Application. Add the framework folder to you project. Embed the framework into your project by clicking on Embedded Binaries on the General tab. You may show two frameworks in your Link Frameworks and Libraries section, just delete one.
 - For Objective-C, ```#import "Rigablue/Rigablue.h"```  in ViewController.m
 - For Swift, create a bridging header and import ```#import "Rigablue/Rigablue.h"``` 

## Setting up BLE Discovery:
To start discovery of devices, you will need to create a ```RigDeviceRequest``` and pass it to a ```RigLeDiscoveryManager``` in the ```discoverDevices``` method.

Create instances of ```RigLeDiscoveryManager```, ```RigLeConnectionManager``` and an optional variable for ```RigDeviceRequest```.

```
let discoveryManager = RigLeDiscoveryManager.sharedInstance()
let connectionManager = RigLeConnectionManager.sharedInstance()
var request : RigDeviceRequest?
```

In ViewDidLoad, create an array of UUIDs that you are scanning for. Next, create a ```RigDeviceRequest```. Assign the viewController as the delegate. You will have to conform to the ```RigDeviceRequestDelegate```, ```RigLeDiscoveryManagerDelegate```, and ```ReLeConnectionManagerDelgate``` protocol. You can then assign self as the delegate for the ConnectionManager and DiscoveryManager at this time.

```
override func viewDidLoad() {
    super.viewDidLoad()

    let uuidList = [deviceUUID]
    request = RigDeviceRequest.deviceRequestWithUuidList(uuidList, timeout: 10, delegate: self, allowDuplicates: true) as? RigDeviceRequest
    connectionManager.delegate = self
    request!.delegate = self
    discoveryManager.startLeInterface()
    discoveryManager.discoverDevices(request)
}
```

Call ```startLeInterface()``` on the discoveryManager to set up the CBCentral. Then call ```discoverDevices```, and pass in the request.



