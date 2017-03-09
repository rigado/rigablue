package com.rigado.rigablue;

/**
 *  IRigCoreBluetoothCommon.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * Interface covering method common to RigCoreBluetooth.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigCoreBluetoothCommon {
    /**
     * This callback method is executed when the power state of the Bluetooth interface changes.
     *
     * @param enabled If true, Bluetooth interface is enabled, disabled otherwise
     */
    public void bluetoothPowerStateChanged(boolean enabled);

    /**
     * This callback method is executed if Bluetooth Low Energy is not supported on the device
     * or if the Android manifest is missing the appropriate bluetooth permissions.
     */
    public void bluetoothDoesNotSupported();
}
