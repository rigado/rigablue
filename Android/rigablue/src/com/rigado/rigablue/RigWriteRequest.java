package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 *  RigWriteRequest.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This class provides a Data Request implementation for writing data to the value of a
 * characteristic.  It is used by RigCoreBluetooth to manage synchronous data requests
 * to the low level Bluetooth APIs.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigWriteRequest implements IRigDataRequest {

    private BluetoothGattCharacteristic mCharacteristic;
    private BluetoothDevice mDevice;
    private byte [] mValue;

    public RigWriteRequest(BluetoothDevice device, BluetoothGattCharacteristic characteristic, byte [] value) {
        mDevice = device;
        mCharacteristic = characteristic;
        mValue = new byte[value.length];
        System.arraycopy(value, 0, mValue, 0, value.length);
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return mCharacteristic;
    }

    public byte [] getValue() {
        return mValue;
    }

    boolean isRequestCharacteristic(BluetoothGattCharacteristic characteristic) {
        if(characteristic == null || mCharacteristic == null) {
            return false;
        }

        if(mCharacteristic.getUuid().equals(characteristic.getUuid())) {
            return true;
        }

        return false;
    }

    boolean isRequestForDevice(BluetoothDevice device) {
        if(device == null || mDevice == null) {
            return false;
        }

        if(mDevice.getAddress().equals(device.getAddress())) {
            return true;
        }

        return false;
    }

    @Override
    public void post(RigService service) {
        if(service == null) {
            return;
        }

        mCharacteristic.setValue(mValue);
        service.writeCharacteristic(mDevice.getAddress(), mCharacteristic);
    }
}
