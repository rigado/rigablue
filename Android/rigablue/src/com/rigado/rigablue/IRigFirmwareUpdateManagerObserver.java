package com.rigado.rigablue;

/**
 *  IRigFirmwareUpdateManagerObserver.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides methods for updating the UI status during a firmware update.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigFirmwareUpdateManagerObserver {

    /**
     * This method is called anytime the firmware manager gets a progress update from the
     * device receiving a firmware update.
     *
     * @param progress The progress percentage of the current update
     */
    void updateProgress(final int progress);

    /**
     * This method is called to provide a string based description of the current status.
     *
     * @param status The current string based status message
     * @param error An error number.  This will always be 0. See {@link #updateFailed(RigDfuError)}
     *              for error handling.
     */
    void updateStatus(final String status, int error);

    /**
     * This method is called after the full firmware update process has completed.
     */
    void didFinishUpdate();

    /**
     * This method is called when an unrecoverable error occurs during a firmware update.
     * @param error An instance of RigDfuError
     */
    void updateFailed(RigDfuError error);
}
