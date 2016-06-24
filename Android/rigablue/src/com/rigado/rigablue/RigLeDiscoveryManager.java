package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

/**
 *  RigLeDiscoveryManager.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class provides a way for apps to discovery available Bluetooth devices and read the
 * advertisement data present in their broadcasts.  This class is a singleton and it is only
 * accessed through the public static class method getInstance().
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigLeDiscoveryManager implements IRigCoreBluetoothDiscoveryObserver {

    /**
     * The list of discovered devices based on the discovery request parameters.
     */
    private volatile ArrayList<RigAvailableDeviceData> mDiscoveredDevices;

    /**
     * If a discovery session is in progress, this flag is true; false otherwise.
     */
    private boolean mIsDiscoveryRunning;

    /**
     * The observer object for this class
     */
    private IRigLeDiscoveryManagerObserver mObserver;

    /**
     * A semaphore for controlling access to the discovered devices list.
     */
    private final Semaphore mLock = new Semaphore(1, true);

    private int searchTime;
    private List<UUID> uuidArrayList;

    /**
     * The singleton instance of this class
     */
    private static RigLeDiscoveryManager instance = null;

    /**
     * The private constructor for this class.
     */
    RigLeDiscoveryManager() {
        mDiscoveredDevices = new ArrayList<>();
        RigCoreBluetooth.getInstance().setDiscoveryObserver(this);
    }

    /**
     * @return Returns the singleton instance of this class.
     */
    public static RigLeDiscoveryManager getInstance() {
        if(instance == null) {
            instance = new RigLeDiscoveryManager();
        }
        return instance;
    }

    /**
     * Starts device discovery using the parameters of the device request.
     *
     * @param request The request for this discovery session
     * @see RigDeviceRequest
     */
    public void startDiscoverDevices(RigDeviceRequest request) {
        RigLog.d("__RigLeDiscoveryManager.startDiscoverDevices__");
        if (request == null || mIsDiscoveryRunning) {
            return;
        }
        int searchTime = 0;
        clearAvailableDevices();

        if (request.getTimeout() > 0) {
            searchTime = request.getTimeout();
        }
        String[] idList = request.getUuidList();
        UUID uuidArrayList[];
        if (idList != null) {
            uuidArrayList = new UUID[idList.length];
            for(int i = 0; i < uuidArrayList.length; i++) {
                uuidArrayList[i] = UUID.fromString(request.getUuidList()[i]);
            }
        } else {
            uuidArrayList = null;
        }

        mIsDiscoveryRunning = true;
        mObserver = request.getObserver();

        RigCoreBluetooth.getInstance().startDiscovery(uuidArrayList, searchTime);
    }

    /**
     * Ends the current discovery session if active.
     */
    public void stopDiscoveringDevices() {
        RigLog.d("__RigLeDiscoveryManager.stopDiscoveringDevices__");
        RigCoreBluetooth.getInstance().stopDiscovery();
        mIsDiscoveryRunning = false;
    }

    /**
     * @return Returns a copy of the current list of discovered devices.
     */
    public ArrayList<RigAvailableDeviceData> getDiscoveredDevices() {
        mLock.acquireUninterruptibly();
        ArrayList<RigAvailableDeviceData> deviceList = new ArrayList<>(mDiscoveredDevices);
        mLock.release();
        return deviceList;
    }

    /**
     * Clears the discovered devices list.
     */
    public void clearAvailableDevices() {
        RigLog.d("__RigLeDiscoveryManager.clearAvailableDevices__");
        mLock.acquireUninterruptibly();
        mDiscoveredDevices.clear();
        mLock.release();
    }

    /**
     * Remove an available device from the discovered devices list.
     * @param device The available device to remove
     * @return Returns true if a device was removed; false otherwise
     */
    public boolean removeAvailableDevice(RigAvailableDeviceData device) {
        RigLog.d("__RigLeDiscoveryManager.removeAvailableDevice__");
        boolean result;

        mLock.acquireUninterruptibly();
        result = mDiscoveredDevices.remove(device);
        mLock.release();

        return result;
    }

    /**
     * Sets the observer of this class
     * @param observer The observer to set
     */
    public void setObserver(IRigLeDiscoveryManagerObserver observer) {
        this.mObserver = observer;
    }

    /**
     * @return Returns true if a device discovery is in progress; false otherwise.
     */
    public boolean isDiscoveryRunning() {
        return mIsDiscoveryRunning;
    }

    /**
     * This callback is received from CoreBluetooth when a device matching the request parameters
     * is discovered.  The data is converted in to a <code>RigAvailableDevice</code> object and
     * then the observer is notified.
     * @param btDevice The discovered device
     * @param rssi The RSSI of the discovered device
     * @param scanRecord The scan response data, will be NULL if no scan response is avaialble
     */
    @Override
    public void didDiscoverDevice(BluetoothDevice btDevice, int rssi, byte [] scanRecord) {
        RigLog.d("RigLeDiscoveryManager.didDiscoverDevice");

        boolean found = false;
        if (rssi > 0) {
            RigLog.d("RigLeDiscoveryManager.rssi > 0");
            /* Sometimes an invalid RSSI is provided by the OS.  Connecting to devices reported in this state will generally result
            * in an unknown connection error. */
            return;
        }

        mLock.acquireUninterruptibly();
        RigLog.d("RigLeDiscoveryManager.didDiscoverDevice:got lock");
        for(RigAvailableDeviceData device : mDiscoveredDevices) {
            if(device.getBluetoothDevice().getAddress().equals(btDevice.getAddress())) {
                found = true;
                break;
            }
        }

        if(!found) {
            RigAvailableDeviceData availableDevice = new RigAvailableDeviceData(btDevice, rssi, scanRecord, System.currentTimeMillis());
            mDiscoveredDevices.add(availableDevice);
            mLock.release();
            if (mObserver != null) {
                mObserver.didDiscoverDevice(availableDevice);
            } else {
                RigLog.d("Observer is null!");
            }
        } else {

            mLock.release();
        }
    }

    /**
     * This callback is received when the discovery operation times out based on the timeout
     * specified in the initial discovery request.  If no timeout is specified, then this callback
     * will not be received.
     */
    @Override
    public void discoveryFinishedByTimeout() {
        RigLog.d("RigLeDiscoveryManager.discoveryFinishedByTimeout");
        mIsDiscoveryRunning = false;
        if (mObserver != null) {
            mObserver.discoveryDidTimeout();
        }
    }

    /**
     * This callback is received if the system Bluetooth power state changes.
     *
     * @param enabled If true, Bluetooth interface is enabled, disabled otherwise
     */
    @Override
    public void bluetoothPowerStateChanged(boolean enabled) {
        RigLog.d("RigLeDiscoveryManager.bluetoothPowerStateChanged");
        if (!enabled) {
            mIsDiscoveryRunning = false;
            clearAvailableDevices();
        }
        if (mObserver != null) {
            mObserver.bluetoothPowerStateChanged(enabled);
        }

    }

    /**
     * This callback is sent if Bluetooth Low Energy is not supported on this device or if the
     * app does not have the appropriate permissions in its manifest.
     */
    @Override
    public void bluetoothDoesNotSupported() {
        RigLog.d("RigLeDiscoveryManager.bluetoothDoesNotSupported");
        mIsDiscoveryRunning = false;
        clearAvailableDevices();
        if (mObserver != null) {
            mObserver.bluetoothDoesNotSupported();
        }
    }
}
