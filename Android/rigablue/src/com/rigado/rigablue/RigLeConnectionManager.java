package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

/**
 *  RigLeConnectionManager.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class provides methods for managing connections to available Bluetooth devices.  This
 * class is a singleton and it is only accessed through the public static class method
 * getInstance().
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigLeConnectionManager implements IRigCoreBluetoothConnectionObserver {

    /**
     * The minimum connection timeout interval in milliseconds.
     */
    private int mMinimumConnectionTimeout;

    /**
     * An array of currently connected devices.  This list includes only those devices connected
     * through the currently running app.  It does not includes other devices connected via other
     * apps running in the system.
     */
    private volatile ArrayList<RigLeBaseDevice> mConnectedDevices;

    /**
     * The observer for the connection manager.  The observer object will receive asynchronous
     * callback messages regarding the operations of the connection manager.
     */
    private IRigLeConnectionManagerObserver mObserver;

    /**
     * The device to which an outstanding connection request is active.
     */
    private RigAvailableDeviceData mConnectingDevice;

    /**
     * This table provides a way to retrieve the advertising data for a given device after
     * successful connection.  The advertisement data may or may not be useful depending on
     * application needs.
     */
    private HashMap<BluetoothDevice, byte[]> mAdvertisingDataList;

    /**
     * Semaphore protection for the connected devices list.
     */
    private final Semaphore mLock = new Semaphore(1, true);

    /**
     * The only instance of this class.
     */
    private static RigLeConnectionManager instance = null;

    /**
     * Private constructor
     */
    RigLeConnectionManager() {
        RigCoreBluetooth.getInstance().setConnectionObserver(this);
        mConnectedDevices = new ArrayList<>();
        mAdvertisingDataList = new HashMap<>();
        mMinimumConnectionTimeout = 5000;
    }

    /**
     * @return Returns the singleton instance of this class
     */
    public static RigLeConnectionManager getInstance()
    {
        if(instance == null)
        {
            instance = new RigLeConnectionManager();
        }
        return instance;
    }

    /**
     * Initiates a connection to the device.  After timeout, if the connection request has not
     * completed successfully, the connection request will be cancelled.
     * @param device The device to connect with
     * @param timeout The amount of time, in milliseconds, to give the connection request before it
     *                is cancelled.
     */
    public void connectDevice(RigAvailableDeviceData device, int timeout) {
        if (timeout != 0 && timeout < mMinimumConnectionTimeout) {
            timeout = mMinimumConnectionTimeout;
        }
        mConnectingDevice = device;
        long connTimeout = timeout;
        byte [] scanRecord = device.getScanRecord();
        if(scanRecord != null) {
            mAdvertisingDataList.put(device.getBluetoothDevice(), scanRecord);
        }

        RigCoreBluetooth.getInstance().connectPeripheral(device.getBluetoothDevice(), connTimeout);
    }

    /**
     * Disconnects from the device.
     * @param device The device to disconnect
     */
    public void disconnectDevice(RigLeBaseDevice device) {
        RigCoreBluetooth.getInstance().disconnectPeripheral(device.getBluetoothDevice());
    }

    /**
     * @return Returns a copy of the list of currently connected devices.
     */
    public ArrayList<RigLeBaseDevice> getConnectedDevices() {
        mLock.acquireUninterruptibly();
        ArrayList<RigLeBaseDevice> deviceList = new ArrayList<>(mConnectedDevices);
        mLock.release();
        return deviceList;
    }

    /**
     * This callback is delivered from the lower level Bluetooth APIs after a successful connection
     * to a device.  The observer of this class will be notified of the device connection.
     * @param btDevice The device to which the connection was made.
     */
    @Override
    public void didConnectDevice(BluetoothDevice btDevice) {
        byte [] scanRecord;
        scanRecord = mAdvertisingDataList.get(btDevice);
        RigLeBaseDevice baseDevice = new RigLeBaseDevice(btDevice, RigCoreBluetooth.getInstance().getServiceList(btDevice.getAddress()), scanRecord);
        mAdvertisingDataList.remove(btDevice);
        RigAvailableDeviceData toRemove = null;


        mLock.acquireUninterruptibly();
        mConnectedDevices.add(baseDevice);
        mLock.release();

        for(RigAvailableDeviceData availDevice : RigLeDiscoveryManager.getInstance().getDiscoveredDevices())
        {
            if(availDevice.getBluetoothDevice().getAddress().equals(btDevice.getAddress())) {
                toRemove = availDevice;
                break;
            }
        }

        /* Now that a valid connection has been made, remove the device from the available list */
        if(toRemove != null) {
            RigLeDiscoveryManager.getInstance().removeAvailableDevice(toRemove);
        }

        if (mObserver != null) {
            mObserver.didConnectDevice(baseDevice);
        }
    }

    /**
     * This callback is received from the lower level Bluetooth API upon a device disconnection.
     * The observer of this class will be notified of this event.
     * @param btDevice The device which disconnected
     */
    @Override
    public void didDisconnectDevice(BluetoothDevice btDevice) {
        RigLeBaseDevice toRemove = null;

        mLock.acquireUninterruptibly();
        for(RigLeBaseDevice device : mConnectedDevices) {
            if(device.getBluetoothDevice().getAddress().equals(btDevice.getAddress())) {
                toRemove = device;
                break;
            }
        }

        if(toRemove != null) {
            mConnectedDevices.remove(toRemove);
        }
        mLock.release();

        if (mObserver != null) {
            mObserver.didDisconnectDevice(btDevice);
        }
    }

    /**
     * This method is sent by CoreBluetooth upon a connection request timeout.  The observer of
     * this class will be notified of this event.
     * @param btDevice The device for which the connection request timed out.
     */
    @Override
    public void connectionDidTimeout(BluetoothDevice btDevice) {
        if (mObserver != null) {
            mObserver.deviceConnectionDidTimeout(mConnectingDevice);
            if(mAdvertisingDataList.containsKey(btDevice)) {
                mAdvertisingDataList.remove(btDevice);
            }
        }
    }

    /**
     * This method is sent by CoreBluetooth upon a connection request failure.  The observer of
     * this class will be notified of this event.
     * @param btDevice The device for which the connection request failed.
     */
    @Override
    public void didFailToConnectDevice(BluetoothDevice btDevice) {
        if (mObserver != null) {
            mObserver.deviceConnectionDidFail(mConnectingDevice);
            if(mAdvertisingDataList.containsKey(btDevice)) {
                mAdvertisingDataList.remove(btDevice);
            }
        }
    }

    /**
     * Sets the observer of this class.
     * @param observer The observer to set
     */
    public void setObserver(IRigLeConnectionManagerObserver observer) {
        this.mObserver = observer;
    }

    /**
     * @return Returns the current observer of this class
     */
    public IRigLeConnectionManagerObserver getObserver() { return mObserver; }

    /**
     * This callback is received if the system Bluetooth power state changes.
     *
     * @param enabled If true, Bluetooth interface is enabled, disabled otherwise
     */
    @Override
    public void bluetoothPowerStateChanged(boolean enabled) {
        if (!enabled) {
            mConnectedDevices.clear();
        }
    }

    /**
     * This callback is sent if Bluetooth Low Energy is not supported on this device or if the
     * app does not have the appropriate permissions in its manifest.
     */
    @Override
    public void bluetoothDoesNotSupported() {
        mConnectedDevices.clear();
    }
}
