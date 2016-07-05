## Rigablue Android

### Overview

Rigablue is an easy to use library for Bluetooth Low Energy (BLE) device integration. Rigablue abstracts away many of the low level details required for typical BLE operations: scanning, connecting, and communications.

### Include the Rigablue SDK in your App

* In Android Studio, create or open an Android app
* Create a ```libs``` directory immediately under the app directory
* Add the source files to ```libs/rigablue``` by either:
  - Copying the rigablue library to libs,
  - **or** adding the SDK as a submodule inside the libs directory
* Add the rigablue module to your app : `File > New > Import Module`
* Select the rigablue directory from the file browser

### Setup

#### REQUIRED PERMISSIONS
As of API 6.0+, all apps that wish to use BLE discovery must include permission ```ACCESS_COARSE_LOCATION``` or ```ACCESS_FINE_LOCATION```.

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<!-- needs ACCESS_COARSE_LOCATION in Android 6.0+ to detect BTLE devices -->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>

```

#### INITIALIZE

In your Application class: `RigCoreBluetooth.initialize(this);`

### Examples

#### DISCOVER DEVICES

```java
  public class DiscoveryClass extends Activity implements IRigLeDiscoveryManagerObserver {

    private static RigLeDiscoveryManager mDiscoveryManager = RigLeDiscoveryManager.getInstance();

    @Override
    public void onCreate(Bundle savedState) {
      super.onCreate(savedState);

      mDiscoveryManager.setObserver(this);
    }

    @Override
    public void onResume() {
      super.onResume();
      //Pass the DiscoveryManager an instance of RigDeviceRequest.
      //This will filter for devices with UUIDs you specify.
      mDiscoveryManager.startDiscoverDevices(getDeviceRequest());
    }

    @Override
    public void onPause() {
      super.onPause();
      mDiscoveryManager.stopDiscoveringDevices();
    }

    private void RigDeviceRequest getDeviceRequest() {
      if(mDeviceRequest == null) {
        final String[] uuids = new String[] {
            Uuids.BMD300_UUID_LED_SERVICE
        };
        mDeviceRequest = new RigDeviceRequest(uuids, 0);
        mDeviceRequest.setObserver(this);
      }

      return mDeviceRequest;
    }
  }
```

You will receive an instance of `RigAvailableDeviceData` when a device matching the provided UUIDs is discovered.

#### CONNECT / DISCONNECT

Implement the `IRigLeConnectionManagerObserver` to receive device connection callbacks:

```java
public class ConnectionClass extends Activity implements IRigLeConnectionManagerObserver {

  private static RigLeConnectionManager mConnectionManager = RigLeConnectionManager.getInstance();

  @Override
  public void onCreate(Bundle savedState) {
    super.onCreate(savedState);

    mConnectionManager.setObserver(this);
  }
}
```

Connecting to a device takes an instance of `RigAvailableDeviceData`:

```java
mConnectionManager.connectDevice(rigAvailableDeviceData);
```

After a successful connection, you will receive the callback `didConnectDevice` with an instance of `RigLeBaseDevice`.

Disconnecting from a device takes an instance of `RigLeBaseDevice`:

```java
mConnectionManager.disconnectDevice(rigLeBaseDevice);
```

#### FIND SERVICES AND CHARACTERISTICS

Implement IRigLeBaseDeviceObserver in your Activity or Fragment
and set it on the device: `mRigablueDevice.setObserver(this)`

Discover services: `mRigablueDevice.runDiscovery()`

After receiving `#discoveryDidComplete`, assign your services and characteristics.

```java
    // get a list of available services from the device
    final List<BluetoothGattService> serviceList = mRigablueDevice.getServiceList();

    //loop through the list using UUIDs to find the desired services
    for(BluetoothGattService service : serviceList) {
        if(service.getUuid().equals(UUID.fromString(Uuids.BMD300_UUID_LED_SERVICE))) {
            mBmdEvalService = service;

            //Once the service has been found, get the desired characteristics using UUIDs.

            mLedLightOneCharacteristic = mBmdEvalService.getCharacteristic(UUID.fromString(Uuids.BMD300_UUID_LED_ONE));

            //Choose to get callbacks when the board receives manual input
            mRigablueDevice.setCharacteristicNotification(mLedLightOneCharacteristic, true);

        }
    }
```

#### READ VALUES

```java
mRigablueDevice.readCharacteristic(mLedLightOneCharacteristic);
```

#### WRITE VALUES

Data must be sent as a byte array.

```java
private final byte[] data = new byte[ (byte) 0xFF ];
mRigablueDevice.writeCharacteristic(mLedLightOneCharacteristic, data);
```
