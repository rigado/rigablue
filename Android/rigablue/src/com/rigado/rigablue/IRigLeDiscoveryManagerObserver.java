package com.rigado.rigablue;

/**
 *  IRigLeDiscoveryManagerObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * @author Eric Stutzenberger
 * @version 1.0
 *
 * This interface provides methods for handling discovery events.
 */
public interface IRigLeDiscoveryManagerObserver {
    /**
     * This method is called when a device is discovery that matches the parameters specified
     * in the device request.
     *
     * @param device The available device information for the discovered device
     * @see RigAvailableDeviceData
     */
    void didDiscoverDevice(RigAvailableDeviceData device);

    /**
     * This method is called when discovery times out.  If not timeout is specified at the
     * start of discovery, then this method will not be called.
     */
    void discoveryDidTimeout();

    /**
     * This method is called if the Bluetooth state changes.
     * @param enabled The enabled or disabled state of Bluetooth on the Android device.
     */
    void bluetoothPowerStateChanged(boolean enabled);

    /**
     * This method is called when Bluetooth is unavailable on the current device or the application
     * manifest is missing the appropriate Bluetooth permissions.
     */
    void bluetoothDoesNotSupported();
}
