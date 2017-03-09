package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
/**
 *  IRigCoreBluetoothDeviceObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides callback methods for the low level bluetooth manager regarding
 * device characteristic state transitions.  It should not be implemented directly by
 * applications using this library.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 *
 */
public interface IRigCoreBluetoothDeviceObserver {
    /**
     * This method is called when the notification state of a characteristic changes
     *
     * @param btDevice The device for which the characteristic state changed
     * @param characteristic The characteristic which changed notification state
     */
    void didUpdateNotificationState(BluetoothDevice btDevice, BluetoothGattCharacteristic characteristic);

    /**
     * This method is called when a characteristic value changes
     *
     * @param btDevice The device for which the characteristic value changed
     * @param characteristic The characteristic which had a value change
     */
    void didUpdateValue(BluetoothDevice btDevice, BluetoothGattCharacteristic characteristic);

    /**
     * This method is called when a characteristic value write has completed
     *
     * @param btDevice The device for which the characteristic value write finished
     * @param characteristic The characteristic which had its value written
     */
    void didWriteValue(BluetoothDevice btDevice, BluetoothGattCharacteristic characteristic);

    void didReadDescriptor(BluetoothDevice btDevice, BluetoothGattDescriptor descriptor);
}
