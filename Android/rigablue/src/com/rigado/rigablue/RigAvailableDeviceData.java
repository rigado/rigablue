package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

/**
 *  RigAvailableDeviceData.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This class provides data storage for available Bluetooth devices.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigAvailableDeviceData {

    /**
     * The following data type values are assigned by Bluetooth SIG.
     * For more details refer to Bluetooth 4.1 specification, Volume 3, Part C, Section 18.
     */
    private static final int DATA_TYPE_LOCAL_NAME_SHORT = 0x08;
    private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 0x09;

    /**
     * The available bluetooth device.
     */
    private BluetoothDevice mBluetoothDevice;

    /**
     * The device name
     */
    private String mName;

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
        this.mName = parseNameFromScanRecord(scanRecord, bluetoothDevice);
    }

    /**
     * Import from Google's {@link android.bluetooth.le.ScanRecord}
     *
     * Parses the local name of the {@link BluetoothDevice} from the {@code scanRecord}.
     * Since {@link BluetoothDevice#getName()} is cached by the bluetooth stack, it is unreliable
     * because it might return the wrong name.
     *
     * @param scanRecord The raw bytes of the {@link android.bluetooth.le.ScanRecord} returned from
     *                   a Bluetooth LE scan.
     * @return The local name of the device or null
     */
    private String parseNameFromScanRecord(byte [] scanRecord, BluetoothDevice device) {
        if (scanRecord == null) {
            return null;
        }

        int currentPos = 0;

        try {
            while (currentPos < scanRecord.length) {
                // length is unsigned int.
                int length = scanRecord[currentPos++] & 0xFF;
                if (length == 0) {
                    break;
                }

                // Not the length includes the length of the field type itself
                final int dataLength = length - 1;
                // fieldType is unsigned int.
                final int fieldType = scanRecord[currentPos++] & 0xFF;
                switch (fieldType) {
                    case DATA_TYPE_LOCAL_NAME_SHORT:
                    case DATA_TYPE_LOCAL_NAME_COMPLETE:
                        return new String(extractBytes(scanRecord, currentPos, dataLength));
                    default:
                        // ignore other data types
                        break;
                }
                currentPos += dataLength;
            }
        } catch (Exception e) {
            RigLog.e("Unable to parse device name!");
        }

        return null;
    }

    /**
     * @return The device name parsed from the raw {@code scanRecord} bytes
     */
    public String getUncachedName() {
        return this.mName;
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

    /**
     * Import from Google's {@link android.bluetooth.le.ScanRecord} class.
     * Helper method to extract bytes from byte array.
     *
     * @param scanRecord The raw bytes received from a Bluetooth LE scan
     * @param start Start index of the data to extract
     * @param length Length of the data to extract
     * @return A new {@code byte []} containing the desired section of the {@code scanRecord}
     */
    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }
}
