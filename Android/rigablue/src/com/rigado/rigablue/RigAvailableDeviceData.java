package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;

/**
 *  RigAvailableDeviceData.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class provides data storage for available Bluetooth devices.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigAvailableDeviceData {

    /**
     * The available bluetooth device.
     */
    private BluetoothDevice mBluetoothDevice;

    /**
     * The RSSI when discovered.
     */
    private int mRssi;

    /**
     * The system timestamp of the discovery.
     */
    private long mDiscoverTime;

    /**
     * The advertising data record.
     */
    private byte[] mScanRecord;

    /**
     * Available device data object constructor.
     *
     * @param bluetoothDevice The available Bluetooth device
     * @param rssi The RSSI of the device's advertisement
     * @param scanRecord The advertising data record
     * @param discoverTime The system time of the discovery
     */
    public RigAvailableDeviceData(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord, long discoverTime) {
        this.mDiscoverTime = discoverTime;
        this.mBluetoothDevice = bluetoothDevice;
        this.mRssi = rssi;
        this.mScanRecord = scanRecord;
    }

    /**
     * @return Returns the RSSI of the discovery.
     */
    public int getRssi() {
        return mRssi;
    }

    /**
     * @return Returns the system time of the discovery
     */
    public long getDiscoverTime() {
        return mDiscoverTime;
    }

    /**
     * @return Returns the advertising data record
     */
    public byte[] getScanRecord() {
        return mScanRecord;
    }

    /**
     * @return Returns the low level Bluetooth device object for this discovery
     */
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    /**
     * Compares two available device data objects for equality.  They are equal if their
     * Bluetooth MAC addresses match.
     * @param o The object for comparison
     * @return Returns true if equal; false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if(!(o instanceof RigAvailableDeviceData)) {
            return false;
        }
        RigAvailableDeviceData deviceData = (RigAvailableDeviceData) o;
        return deviceData.getBluetoothDevice().getAddress().equals(mBluetoothDevice.getAddress());
    }

    /**
     * Converts the available device data object to a string.
     * @return Returns the Bluetooth MAC address as a string
     */
    @Override
    public String toString() {
        if(mBluetoothDevice == null) {
            return "";
        }
        return mBluetoothDevice.getAddress();
    }
}
