package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;

/**
 *  IRigLeConnectionManagerObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides methods for handling device connection events.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigLeConnectionManagerObserver {
    /**
     * This method is called when a successful connection is made to a device.
     *
     * @param device The newly connected device
     */
    void didConnectDevice(RigLeBaseDevice device);

    /**
     * This method is called when a device disconnects.  Since the RigLeBaseDevice object is no
     * longer valid after disconnection, this method provides the low level Bluetooth device in
     * case is it relevant to the disconnection.
     *
     * @param btDevice The disconnected Bluetooth Device object
     */
    void didDisconnectDevice(BluetoothDevice btDevice);

    /**
     * This method is called if a connection to a device fails.
     *
     * @param device The available device data for the failed connection request
     */
    void deviceConnectionDidFail(RigAvailableDeviceData device);

    /**
     * This method is called if the connection to a device times out.
     *
     * @param device The available device data for the connection request
     */
    void deviceConnectionDidTimeout(RigAvailableDeviceData device);
}
