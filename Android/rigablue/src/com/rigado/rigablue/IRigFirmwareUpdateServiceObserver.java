package com.rigado.rigablue;

/**
 *  IRigFirmwareUpdateServiceObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides methods for dealing with state changes of the Bluetooth device
 * during firmware updates.  It should not be implemented by applications using Rigablue.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigFirmwareUpdateServiceObserver {
    /**
     * This method is called when control point notifications have been enabled.
     */
    void didEnableControlPointNotifications();

    /**
     * This method is called when the value for the control point changes either due to a
     * notification or a read request.
     *
     * @param value The updated control point value
     */
    void didUpdateValueForControlPoint(byte [] value);

    /**
     * This method is called when the value for the control point has been written.
     */
    void didWriteValueForControlPoint();

    /**
     * This method is called when the update peripheral has been connected.  It may or may
     * not be used depending on the update implementation.
     */
    void didConnectPeripheral();

    /**
     * This method is called if the update peripheral disconnects.
     */
    void didDisconnectPeripheral();

    /**
     * This method is called when all services and characteristics have been fully discovered
     * and read for the DFU service.
     */
    void didDiscoverCharacteristicsForDFUService();
}
