package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;

/**
 *  IRigCoreBluetoothDiscoveryObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides methods for device discovery operations performed by the low
 * level Bluetooth stack.  It should not be implemented by applications using Rigablue.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 *
 */
public interface IRigCoreBluetoothDiscoveryObserver extends IRigCoreBluetoothCommon {
    /**
     * This method is called when a device matching the appropriate discovery parameters
     * is found.
     *
     * @param btDevice The discovered device
     * @param rssi The RSSI of the discovered device
     * @param scanRecord The scan response data, will be NULL if no scan response is avaialble
     */
    void didDiscoverDevice(BluetoothDevice btDevice, int rssi, byte [] scanRecord);

    /**
     * This method is called when a discovery session finishes after the specified timeout.
     */
    void discoveryFinishedByTimeout();
}
