package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 *  RigNotificationStateChangeRequest.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This class provides a Data Request implementation for request changes in notification
 * state for a characteristic.  It is used by RigCoreBluetooth to manage synchronous data requests
 * to the low level Bluetooth APIs.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigNotificationStateChangeRequest implements IRigDataRequest {

    BluetoothDevice mDevice;
    BluetoothGattCharacteristic mCharacteristic;
    boolean mEnableState;

    public RigNotificationStateChangeRequest(BluetoothDevice device, BluetoothGattCharacteristic characteristic,
                                             boolean enableState) {
        mDevice = device;
        mCharacteristic = characteristic;
        mEnableState = enableState;
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public BluetoothGattCharacteristic getCharacteristic() {
        return mCharacteristic;
    }

    public boolean getEnableState() {
        return mEnableState;
    }

    @Override
    public void post(RigService service) {
        service.setCharacteristicNotification(mDevice.getAddress(),
                mCharacteristic, mEnableState);
    }
}
