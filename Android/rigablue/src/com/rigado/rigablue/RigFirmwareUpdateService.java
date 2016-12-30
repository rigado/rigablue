package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.support.annotation.NonNull;

import java.util.List;
import java.util.UUID;

import static android.R.attr.data;

/**
 *  RigFirmwareUpdateService.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class provides glueing of the Rigado DFU service to the firmware update manager.  It is
 * responsible for interfacing with the Device being updated and providing feedback to the firmware
 * update manager object.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigFirmwareUpdateService implements IRigLeConnectionManagerObserver,
                                                 IRigLeBaseDeviceObserver {

	private static final String kupdateDfuServiceUuidStringBMD200 = "00001530-1212-efde-1523-785feabcd123";
    private static final String kupdateDfuControlPointUuidStringBMD200 = "00001531-1212-efde-1523-785feabcd123";
    private static final String kupdateDfuPacketCharUuidStringBMD200 = "00001532-1212-efde-1523-785feabcd123";

    private static final String kupdateDfuServiceUuidStringBMD300 = "41c89030-1756-4c30-93cc-a8fcc2fb0202";
    private static final String kupdateDfuControlPointUuidStringBMD300 = "41c89032-1756-4c30-93cc-a8fcc2fb0202";
    private static final String kupdateDfuPacketCharUuidStringBMD300 = "41c89031-1756-4c30-93cc-a8fcc2fb0202";

    public static final String kDisUuidString = "0000180a-0000-1000-8000-00805f9b34fb";
    public static final String kDisFwVersionUuidString = "00002a26-0000-1000-8000-00805f9b34fb";
    public static final String kDisModelNumberUuidString = "00002a24-0000-1000-8000-00805f9b34fb";
    
    private static final String kSecureBootloaderModelNumber = "Rigado Secure DFU";

    // UUID strings matching the device we want to update. Each will be assigned to one of the device-specific
    // strings above upon connection (e.g., kupdateDfuServiceUuidStringBMD200)
    private String mUpdateDfuServiceUuidString = null;
    private String mUpdateDfuControlPointUuidString = null;
    private String mUpdateDfuPacketCharUuidString = null;


    /**
     * The available device data for the bootloader device.
     */
    RigAvailableDeviceData mAvailDevice;

    /**
     * The device being updated.
     */
    RigLeBaseDevice mUpdateDevice;

    /**
     * The device before the update has started.
     */
    RigLeBaseDevice mInitialNonBootloaderDevice;

    /**
     * The address of the device that will be updated.
     */
    String mUpdateDeviceAddress;

    /**
     * The Dfu service object.
     */
    BluetoothGattService mDfuService;

    /**
     * The control point characteristic for the dfu service.
     */
    BluetoothGattCharacteristic mControlPoint;

    /**
     * The packet characterisic for the dfu service.
     */
    BluetoothGattCharacteristic mPacketChar;

    /**
     * The device information service for the bootloader device.
     */
    BluetoothGattService mDisService;

    /**
     * The firmware version of the bootloader.
     */
    BluetoothGattCharacteristic mDisFirmwareVersionChar;

    /**
     * The model number of the bootloader.
     */
    BluetoothGattCharacteristic mDisModelNumberChar;

    /**
     * The previous connection observer.  #RigFirmwareUpdateService will take on the role of the
     * connection observer during the firmware update.  The previous connection observer will
     * be restored upon completion (or failure) of the firmware update.
     */
    IRigLeConnectionManagerObserver mOldConnectionObserver;

    /**
     * The observer of this object.
     */
    IRigFirmwareUpdateServiceObserver mObserver;

    /**
     * If true, then the firmware update service will attempt to reconnection upon the next
     * disconnection.
     */
    boolean mShouldReconnectToDevice;

    /**
     * If true, then the firmware update service will always attempt reconnection to the bootloader
     * device upon any disconnection.
     */
    boolean mAlwaysReconnectOnDisconnect;

    /**
     * If true, then the currently connected bootloader device is a secure bootloader device.  This
     * does not necessarily mean the bootloader has been secured, only that it is the secure
     * version of the bootloader.  In general, this field is only for backwards compatibility with
     * older bootloaders that did not have security features.
     */
    boolean mIsSecureDfu;

    /**
     * Creates a new firmware service object.
     */
    public RigFirmwareUpdateService() {
        mOldConnectionObserver = RigLeConnectionManager.getInstance().getObserver();
        RigLeConnectionManager.getInstance().setObserver(this);
        mReconnectAttempts = 0;
        mShouldReconnectToDevice = false;
        mIsSecureDfu = false;
    }

    /**
     * Sets the observer for this instance
     * @param observer The observer to set
     */
    public void setObserver(IRigFirmwareUpdateServiceObserver observer) {
        mObserver = observer;
    }

    /**
     * @deprecated
     * @return the UUID String of the device firmware update service for the BMD-200
     */
    public String getDfuServiceUuidString() {
    	return kupdateDfuServiceUuidStringBMD200;
    }

    /**
     * @return the UUID Strings of the firmware update services. Each module family
     *         may have a different service UUID.
     */
    public String[] getDfuServiceUuidStrings() {
        String[] uuids = { kupdateDfuServiceUuidStringBMD200, kupdateDfuServiceUuidStringBMD300 };
        return uuids;
    }

    /**
     * Sets the state of the should reconnect private variable.
     *
     * @param state The state to set
     */
    public void setShouldReconnectState(boolean state) {
        mShouldReconnectToDevice = state;
    }

    /**
     * Sets the state of the should always reconnect private variable.
     *
     * @param state The state to set
     */
    public void setShouldAlwaysReconnectState(boolean state) {
        mAlwaysReconnectOnDisconnect = state;
    }

    /**
     * Connects to the device being updated.
     */
    public void connectDevice() {
        RigLog.d("__RigFirmwareUpdateService.connectDevice__");
        //TODO: need to acquire the device address here so that we can issue a new connection to the device
        RigLeConnectionManager.getInstance().connectDevice(mAvailDevice, 20000);
    }

    /**
     * Reconnects to the device being updated.  This is left for backwards compatibility with
     * old bootloaders that required a full erase and reboot before the update could start.
     */
    public void reconnectDevice() {
        RigLog.d("__RigFirmwareUpdateService.reconnectPeripheral__");
        mShouldReconnectToDevice = true;
        mReconnectAttempts = 0;
        RigLeConnectionManager.getInstance().disconnectDevice(mUpdateDevice);
    }

    /**
     * Sets the current update device.
     * @param device The device to set
     */
    public void setDevice(RigLeBaseDevice device) {
        mUpdateDevice = device;
        mUpdateDevice.setObserver(this);
        mUpdateDeviceAddress = mUpdateDevice.getBluetoothDevice().getAddress();
        mAvailDevice = new RigAvailableDeviceData(mUpdateDevice.getBluetoothDevice(), 0, null, 0);
    }

    /**
     * Set the device object that is used before invoking the bootloader.
     * @param device The device to set
     */
    public void setInitialNonBootloaderDevice(RigLeBaseDevice device) {
        mInitialNonBootloaderDevice = device;
    }

    /**
     * Notification that initial non-bootloader device disconnected.
     */
    public void didDisconnectInitialNonBootloaderDevice() {
        if(mOldConnectionObserver != null) {
            mOldConnectionObserver.didDisconnectDevice(mInitialNonBootloaderDevice.getBluetoothDevice());
        }
    }

    /**
     * Starts service discovery on the update device.
     */
    public void triggerServiceDiscovery() {
        RigLog.d("__RigFirmwareUpdateService.triggerServiceDiscovery__");
        if(mUpdateDevice != null) {
            if(RigCoreBluetooth.getInstance().getDeviceConnectionState(mUpdateDevice.getBluetoothDevice()) ==
                    BluetoothProfile.STATE_CONNECTED) {
                mUpdateDevice.runDiscovery();
            } else {
                connectDevice();
            }
        }
    }

    /**
     * Instructs the #RigFirmwareUpdateService to search the update device for the DFU and
     * DIS services and to fill out the characteristic objects.
     * @return Returns true if successful; false otherwise
     */
    public boolean getServiceAndCharacteristics() {
        setDfuUUIDsFromDevice(mUpdateDevice);

        if (mUpdateDfuServiceUuidString == null ||
                mUpdateDfuControlPointUuidString == null ||
                mUpdateDfuPacketCharUuidString == null) {
            RigLog.e("DFU UUIDs have not been set");
            return false;
        }

        UUID dfuServiceUuid = UUID.fromString(mUpdateDfuServiceUuidString);
        UUID controlPointUuid = UUID.fromString(mUpdateDfuControlPointUuidString);
        UUID packetUuid = UUID.fromString(mUpdateDfuPacketCharUuidString);
        
        UUID disServiceUuid = UUID.fromString(kDisUuidString);
        UUID disFwVersionUuid = UUID.fromString(kDisFwVersionUuidString);
        UUID disModelNumberUuid = UUID.fromString(kDisModelNumberUuidString);

        mDfuService = null;
        for(BluetoothGattService service : mUpdateDevice.getServiceList()) {
            if(service.getUuid().equals(dfuServiceUuid)) {
                mDfuService = service;
            } else if(service.getUuid().equals(disServiceUuid)) {
            	mDisService = service;
            }
        }

        if(mDfuService == null) {
            RigLog.e("Did not find Dfu Service!");
            return false;
        }
        
        if(mDisService == null) {
        	RigLog.e("Did not find Dis service for Bootloader");
        	return false;
        }

        for(BluetoothGattCharacteristic characteristic : mDfuService.getCharacteristics()) {
            if(characteristic.getUuid().equals(controlPointUuid)) {
                mControlPoint = characteristic;
            } else if(characteristic.getUuid().equals(packetUuid)) {
                mPacketChar = characteristic;
            }
        }

        if(mControlPoint == null || mPacketChar == null) {
            RigLog.e("One or more dfu characteristics missing!");
            return false;
        }
        
        for(BluetoothGattCharacteristic characteristic : mDisService.getCharacteristics()) {
        	if(characteristic.getUuid().equals(disFwVersionUuid)) {
        		mDisFirmwareVersionChar = characteristic;
        	} else if(characteristic.getUuid().equals(disModelNumberUuid)) {
        		mDisModelNumberChar = characteristic;
        	}
        }
        
        String modelNumber = mDisModelNumberChar.getStringValue(0);
        if(modelNumber.equals(kSecureBootloaderModelNumber)) {
        	mIsSecureDfu = true;
        }

        return true;
    }

    /**
     * @return Returns true if the bootloader is the secure bootloader; false otherwise.
     */
    public boolean isSecureDfu() {
    	return mIsSecureDfu;
    }

    /**
     * Disconnects the update device.
     */
    public void disconnectDevice() {
        RigLeConnectionManager.getInstance().disconnectDevice(mUpdateDevice);
    }

    /**
     * Deprecated as of v1.2.0.
     *
     * Writes data to the update device's control point characteristic.
     * @param data The data to write
     */
    @Deprecated
    public void writeDataToControlPoint(byte[] data) {
        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return;
        }

        if(mControlPoint == null) {
            RigLog.e("Dfu control point is null!");
            return;
        }

        if(data == null) {
            RigLog.e("Attempted to write null data to control point!");
            return;
        }

        mUpdateDevice.writeCharacteristic(mControlPoint, data);
    }

    /**
     * Writes data to the update device's control point characteristic
     *
     * @param writeData The data to write as a {@code byte []}
     * @return An instance of {@link RigDfuError} on failure, {@code null} on success.
     */
    RigDfuError writeToControlPoint(@NonNull byte[] writeData) {
        if(mUpdateDevice == null) {
            RigLog.e("Not connected to a peripheral!");
            return RigDfuError.errorFromCode(RigDfuError.BAD_PERIPHERAL);
        }

        if(mControlPoint == null) {
            RigLog.e("Control point characteristic is missing!");
            return RigDfuError.errorFromCode(RigDfuError.CONTROL_POINT_CHARACTERISTIC_MISSING);
        }

        mUpdateDevice.writeCharacteristic(mControlPoint, writeData);
        return null;
    }

    /**
     * Deprecated as of v1.2.0.
     *
     * Writes data to the update device's packet characteristic.
     * @param data The data to write.
     */
    @Deprecated
    public void writeDataToPacketCharacteristic(byte [] data) {
        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return;
        }

        if(mPacketChar == null) {
            RigLog.e("Dfu packet characteristic is null!");
            return;
        }

        if(data == null) {
            RigLog.e("Attempted to write null data to packet characteristic!");
            return;
        }

        mUpdateDevice.writeCharacteristic(mPacketChar, data);
    }

    /**
     * Writes data to the update device's packet characteristic.
     *
     * @param writeData The data to write as a {@code byte []}
     * @return An instance of {@link RigDfuError} on failure, {@code null} on success.
     */
    RigDfuError writeToPacketCharacteristic(byte [] writeData) {
        if(mUpdateDevice == null) {
            RigLog.e("Not connected to a peripheral!");
            return RigDfuError.errorFromCode(RigDfuError.BAD_PERIPHERAL);
        }

        if(mPacketChar == null) {
            RigLog.e("Packet characteristic missing!");
            return RigDfuError.errorFromCode(RigDfuError.CONTROL_POINT_CHARACTERISTIC_MISSING);
        }

        mUpdateDevice.writeCharacteristic(mPacketChar, writeData);
        return null;
    }

    /**
     * Enables notifications for the DFU service control point.
     */
    RigDfuError enableControlPointNotifications() {
        RigLog.d("__RigFirmwareUpdateService.enableControlPointNotifications__");

        if(mUpdateDevice == null) {
            RigLog.e("Update device is null!");
            return RigDfuError.errorFromCode(RigDfuError.BAD_PERIPHERAL);
        }

        if(mControlPoint == null) {
            RigLog.e("Dfu control point is null!");
            return RigDfuError.errorFromCode(RigDfuError.CONTROL_POINT_CHARACTERISTIC_MISSING);
        }

        mUpdateDevice.setCharacteristicNotification(mControlPoint, true);
        return null;
    }

    /**
     * Finishes the update by reassigning the connection delegate.
     */
    public void completeUpdate() {
        RigLog.d("__RigFirmwareUpdateService.completeUpdate__");
        RigLeConnectionManager.getInstance().setObserver(mOldConnectionObserver);
    }

    //region IRigLeConnectionManagerObserver methods
    /**
     * Called when a device connects.  If it is the update device, then it starts service and
     * characteristic discovery.
     *
     * @param device The newly connected device
     */
    @Override
    public void didConnectDevice(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateService.didConnectDevice__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
            mUpdateDevice = device;
            mUpdateDevice.setObserver(this);
            mUpdateDevice.runDiscovery();
        }
    }

    /**
     * The timeout in milliseconds for reconnection attempts
     */
    private final static int CONNECTION_TIMEOUT = 10000;

    /**
     * The number of times a reconnection to the update device has been attempted.
     */
    private int mReconnectAttempts;

    /**
     * The max number of times Rigablue will attempt to reconnect.
     * This is currently set to zero as no tested devices have been able to reconnect.
     */
    private final static int MAX_RECONNECT_ATTEMPTS = 0;

    private void maybeReconnect(BluetoothDevice device) {
        if((mShouldReconnectToDevice || mAlwaysReconnectOnDisconnect) && mReconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            RigLog.d(String.format("Reconnecting to %s...", device.getAddress()));
            RigAvailableDeviceData deviceData = new RigAvailableDeviceData(device, 0, null, 0);
            RigLeConnectionManager.getInstance().connectDevice(deviceData, CONNECTION_TIMEOUT);
            mReconnectAttempts++;
        } else {
           mObserver.didDisconnectPeripheral();
        }
    }

    /**
     * Called when a device disconnects.  Handles the logic for what should happen based on the
     * state of the reconnection variables.
     *
     * @param btDevice The disconnected Bluetooth Device object
     */
    @Override
    public void didDisconnectDevice(BluetoothDevice btDevice) {
        RigLog.d("__RigFirmwareUpdateService.didDisconnectDevice__");
        if(btDevice.getAddress().equals(mUpdateDeviceAddress)) {
            maybeReconnect(btDevice);

        } else {
            /* This may be the original device disconnect.  Pass it on to the original connection
             * manager delegate.
             */
            if (mOldConnectionObserver != null) {
                mOldConnectionObserver.didDisconnectDevice(btDevice);
            }
        }
    }

    /**
     * Called if the connection to the update device fails.  Handles reconnections as appropriate.
     * @param device The available device data for the failed connection request
     */
    @Override
    public void deviceConnectionDidFail(RigAvailableDeviceData device) {
        RigLog.d("__RigLeFirmwareUpdateService.deviceConnectionDidFail__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
            maybeReconnect(device.getBluetoothDevice());
        }
    }

    /**
     * Called if the connection to the update device times out.  Handles reconnection if appropriate.
     * @param device The available device data for the connection request
     */
    @Override
    public void deviceConnectionDidTimeout(RigAvailableDeviceData device) {
        RigLog.d("__RigLeFirmwareUpdateService.deviceConnectionDidTimeout__");
        if(device.getBluetoothDevice().getAddress().equals(mUpdateDeviceAddress)) {
                maybeReconnect(device.getBluetoothDevice());

        }
    }
    //endregion

    //region IRigLeBaseDeviceObserver methods
    /**
     * Called when the value for a characteristic changes due to a read request or notification.
     * @param device The device for which the characteristic value updated
     * @param characteristic The characteristic that updated
     */
    @Override
    public void didUpdateValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
            mObserver.didUpdateValueForControlPoint(characteristic.getValue());
        }
    }

    /**
     * Called when the notification state of a characteristic is updated successfully.
     *
     * @param device The device for which the characteristic state changed
     * @param characteristic The characteristic for which notifications were enabled
     */
    @Override
    public void didUpdateNotifyState(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
           mObserver.didEnableControlPointNotifications();
        }
    }

    /**
     * Called when the value of a characteristic was written successfully.
     *
     * @param device The device for which the characteristic value was written
     * @param characteristic The characteristic which had its value written
     */
    @Override
    public void didWriteValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic) {
        if(characteristic.getUuid().equals(mControlPoint.getUuid())) {
            mObserver.didWriteValueForControlPoint();
        }
    }

    /**
     * Called when device discovery has been completed by a #RigLeBaseDevice object.
     *
     * @param device The device for which discovery completed.
     */
    @Override
    public void discoveryDidComplete(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateService.discoveryDidComplete__");
        mUpdateDevice = device;
        if(!getServiceAndCharacteristics()) {
            RigLog.e("Connected to invalid device!");

        }
        mUpdateDevice.setObserver(this);
        mObserver.didDiscoverCharacteristicsForDFUService();
    }
    //endregion

    /**
     * Detects the Dfu version (200 or 300) and sets the firmware
     * update UUIDs accordingly. Clears the list of UUIDs if
     * a matching service is not found.
     *
     * @param device An instance of #RigLeBaseDevice
     */
    private void setDfuUUIDsFromDevice (RigLeBaseDevice device) {
        List<BluetoothGattService> fwServices = device.getServiceList();
        for (BluetoothGattService service: fwServices) {
            String uuidString = service.getUuid().toString();
            if (kupdateDfuServiceUuidStringBMD200.equals(uuidString)) {
                mUpdateDfuServiceUuidString = kupdateDfuServiceUuidStringBMD200;
                mUpdateDfuControlPointUuidString = kupdateDfuControlPointUuidStringBMD200;
                mUpdateDfuPacketCharUuidString = kupdateDfuPacketCharUuidStringBMD200;
                return;
            } else if (kupdateDfuServiceUuidStringBMD300.equals(uuidString)) {
                mUpdateDfuServiceUuidString = kupdateDfuServiceUuidStringBMD300;
                mUpdateDfuControlPointUuidString = kupdateDfuControlPointUuidStringBMD300;
                mUpdateDfuPacketCharUuidString = kupdateDfuPacketCharUuidStringBMD300;
                return;
            }
        }
        RigLog.d("Cannot set DFU UUIDs; no matching service found.");
        clearDfuUUIDs();
    }

    /**
     * Resets the Dfu firmware update UUIDs
     */
    private void clearDfuUUIDs() {
        mUpdateDfuServiceUuidString = null;
        mUpdateDfuControlPointUuidString = null;
        mUpdateDfuPacketCharUuidString = null;
    }
}
