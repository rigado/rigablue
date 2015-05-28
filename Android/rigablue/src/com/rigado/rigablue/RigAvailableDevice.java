package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;

/**
 *  RigAvailableDevice.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class provides information about advertising Bluetooth devices which match the
 * discovery request parameters provided to the RigLeDiscoveryManager.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigAvailableDevice {

    /**
     * The Bluetooth device object as provided by the low level Bluetooth api.
     */
    private BluetoothDevice mBluetoothDevice;

    /**
     * The RSSI of the most recent advertisement.
     */
    private int mRssi;

    /**
     * A timestamp of the last scan event for this device.
     */
    private long mDiscoverTime;

    /**
     * The byte array of the scan response if available.  This will be <code>null</code> if no scan response
     * is avaialble.
     */
    private byte[] mScanRecord;

    /**
     * Contrusts a RigAvailableDevice object.
     *
     * @param bluetoothDevice The Bluetooth device for this discovery event
     * @param rssi The RSSI of the discovered device
     * @param scanRecord The scan response record if available
     * @param discoverTime The system when this device was discovered
     */
    public RigAvailableDevice(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord, long discoverTime) {
        this.mDiscoverTime = discoverTime;
        this.mBluetoothDevice = bluetoothDevice;
        this.mRssi = rssi;
        this.mScanRecord = scanRecord;
    }

    /**
     * @return Returns the RSSI of the available device
     */
    public int getRssi() {
        return mRssi;
    }

    /**
     * @return Returns the discovery time of the available device
     */
    public long getDiscoverTime() {
        return mDiscoverTime;
    }

    /**
     * @return Returns an array of bytes represeting the scan response data
     */
    public byte[] getScanRecord() {
        return mScanRecord;
    }

    /**
     * @return Returns the low level Bluetooth device object
     */
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    /**
     * This function provides a way to update device data.
     * @param rigAvailableDevice The device data to update
     */
    public void setDeviceData(RigAvailableDevice rigAvailableDevice) {
        mRssi = rigAvailableDevice.getRssi();
        mDiscoverTime = rigAvailableDevice.getDiscoverTime();
        mScanRecord = rigAvailableDevice.getScanRecord();
    }

    /**
     * Overrides the equals function to provide a way to match available data objects.  The match
     * is based on the Bluetooth address of the different devices since advertisement data could
     * be exactly the same.
     *
     * @param o Object to compare against
     * @return Returns true if the two available device objects match, false otherwise.
     */
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof RigAvailableDevice)) {
            return false;
        }
        RigAvailableDevice rigAvailableDevice = (RigAvailableDevice) o;
        return rigAvailableDevice.getBluetoothDevice().getAddress().equals(this.getBluetoothDevice().getAddress());
    }

    /**
     * Provides a string representation of an available device object.
     *
     * @return The Bluetooth MAC address for the device as a string
     */
    @Override
    public String toString() {
        return mBluetoothDevice.getAddress();
    }
}
