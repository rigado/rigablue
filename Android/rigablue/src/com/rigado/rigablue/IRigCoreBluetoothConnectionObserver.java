package com.rigado.rigablue;
import android.bluetooth.BluetoothDevice;

/**
 *  IRigCoreBluetoothConnectionObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface is used by the low level bluetooth operations in Rigablue.  It should not
 * be implemented directly by application objects.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigCoreBluetoothConnectionObserver extends IRigCoreBluetoothCommon {
    /**
     * This method is called whenever a connection request has successfully connected to a
     * device.
     *
     * @param btDevice The device to which the connection was made.
     */
    void didConnectDevice(BluetoothDevice btDevice);

    /**
     * This method is called if the timeout expires prior to connection completion.  If no
     * timeout is specified for the connection, then this method will not be called.
     *
     * @param btDevice The device for which the connection request timed out.
     */
    void connectionDidTimeout(BluetoothDevice btDevice);

    /**
     * This method is called anytime a device is disconnected.  The may occur either via a
     * requested disconnection or due to an out of range or connection timeout.
     *
     * @param btDevice The device which disconnect
     */
    void didDisconnectDevice(BluetoothDevice btDevice);

    /**
     * This method is called anytime a device connection request fails.
     *
     * @param btDevice The device for which the connection request failed.
     */
    void didFailToConnectDevice(BluetoothDevice btDevice);
}
