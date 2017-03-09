package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 *  IRigCoreListener.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface listens to events coming directly from the low level Bluetooth interface
 * on Android.  It should not be directly implemented by applications using Rigablue.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 *
 */
public interface IRigCoreListener {
    void onActionGattReadRemoteRssi(BluetoothDevice bluetoothDevice, int rssi);
    void onActionGattConnected(BluetoothDevice bluetoothDevice);
    void onActionGattDisconnected(BluetoothDevice bluetoothDevice);
    void onActionGattFail(BluetoothDevice bluetoothDevice);
    void onActionGattServicesDiscovered(BluetoothDevice bluetoothDevice);
    void onActionGattDataAvailable(BluetoothGattCharacteristic characteristic, BluetoothDevice bluetoothDevice);
    void onActionGattDataNotification(BluetoothGattCharacteristic characteristic, BluetoothDevice bluetoothDevice);
    void onActionGattDescriptorWrite(BluetoothGattDescriptor descriptor, BluetoothDevice bluetoothDevice);
    void onActionGattCharWrite(BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic);
    void onActionGattDescriptorRead(BluetoothDevice bluetoothDevice, BluetoothGattDescriptor descriptor);
}
