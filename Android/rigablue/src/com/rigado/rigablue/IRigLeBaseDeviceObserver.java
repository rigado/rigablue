package com.rigado.rigablue;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;

/**
 *  IRigLeBaseDeviceObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides methods for getting state updates from RigLeBaseDevice objects.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigLeBaseDeviceObserver {
    /**
     * This method is called when the value for a characteristic changes either due to an
     * asynchronous notification or if the value of the characteristic was requested via a
     * read operation.
     *
     * @param device The device for which the characteristic value updated
     * @param characteristic The characteristic that updated
     */
    void didUpdateValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic);

    /**
     * This method is called when notifications are successfully enabled or disabled for a
     * characteristic.
     *
     * @param device The device for which the characteristic state changed
     * @param characteristic The characteristic for which notifications were enabled
     */
    void didUpdateNotifyState(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic);

    /**
     * This method is called when the value for a characteristic has been successfully written by
     * the low level android APIs.
     *
     * @param device The device for which the characteristic value was written
     * @param characteristic The characteristic which had its value written
     */
    void didWriteValue(RigLeBaseDevice device, BluetoothGattCharacteristic characteristic);

    /**
     * This method is called when discovery of characteristic and services completes after calling
     * the runDiscovery method.
     *
     * @param device The device for which discovery completed.
     * @see RigLeBaseDevice#runDiscovery()
     */
    void discoveryDidComplete(RigLeBaseDevice device);

}
