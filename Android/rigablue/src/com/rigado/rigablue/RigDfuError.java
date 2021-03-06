package com.rigado.rigablue;

import java.util.HashMap;
import java.util.Map;

/**
 * Instances of RigDfuError are passed to callbacks when a bootloader operation fails.
 * They contain a description of the specific error that occurred.
 */

public class RigDfuError {

    private String errorMessage;
    private int errorCode;

    /*
     * Port of RigDfuError.h from iOS version
     */

    /** An invalid/null value was encountered where an Android BluetoothDevice was expected */
    public static final int BAD_PERIPHERAL = -1;
    /** Unused in iOS */
    public static final int CONTROL_POINT_CHARACTERISTIC_MISSING = -2;
    /** An invalid/null value was encountered where a RigLeBaseDevice was expected */
    public static final int BAD_DEVICE = -3;
    /** Not currently used. See BAD_PERIPHERAL. */
    public static final int PERIPHERAL_NOT_SET = -4;
    /** The firmware update request was initialized with a null parameter */
    public static final int INVALID_PARAMETER = -5;
    /**
     * Failed to validate the firmware image after a successful transfer. It is possible that trying
     * again may fix it.
     */
    public static final int IMAGE_VALIDATION_FAILURE = -6;
    /**
     * Failed to activate the firmware. It is possible that trying again may fix it.
     */
    public static final int IMAGE_ACTIVATION_FAILURE = -7;
    /**
     * The CRC of the image on the device before patching does not match the expected CRC. This is
     * an unrecoverable error that has to do with having the wrong patch file.
     */
    public static final int PATCH_CURRENT_IMAGE_CRC_FAILURE = -8;
    /**
     * Replaces error {@code IMAGE_VALIDATION_FAILURE} for patch binaries. This means that the CRC
     * of the firmware after patching did not match the expected CRC. Retrying may recover from
     * this error, but unlikely.
     */
    public static final int POST_PATCH_IMAGE_CRC_FAILURE = -9;
    /**
     * This means that the update manager could not connect to RigDfu. Currently unused, we handle
     * connection failures with {@code CONNECTION_FAILED}, {@code CONNECTION_TIMEOUT}, and
     * {@code BOOTLOADER_DISCONNECT}
     */
    public static final int COULD_NOT_CONNECT = -10;

    /**
     * An unknown error occurred.
     */
    public static final int UNKNOWN_ERROR = -11;

    /*
     * Additional errors based on iOS error messages that did not
     * have an associated RigDfuError object
     */
    public static final int CONNECTION_FAILED = -30;
    public static final int CONNECTION_TIMEOUT = -31;
    public static final int DISCOVERY_TIMEOUT = -32;
    /** Unused */
    public static final int PATCH_INIT_WRITE_FAILURE = -33;
    /** Unused */
    public static final int FIRMWARE_VALIDATION_INIT_FAILURE = -34;
    /**
     * Called if the bootloader fails to connect or if it disconnects during the update process.
     */
    public static final int BOOTLOADER_DISCONNECT = -35;
    /**
     * An in progress firmware update was cancelled by the user.
     */
    public static final int FIRMWARE_UPDATE_CANCELLED = -36;

    private static final Map<Integer, String> errorReasons;

    static {
        errorReasons = new HashMap<>();
        errorReasons.put(BAD_PERIPHERAL, "Could not find RigDfu device! - Bad Peripheral.");
        errorReasons.put(CONTROL_POINT_CHARACTERISTIC_MISSING,
                "Could not initialize firmware update service! " +
                        "Missing control point characteristic.");
        errorReasons.put(BAD_DEVICE, "Could not find RigDfu device! - Bad Device.");
        errorReasons.put(PERIPHERAL_NOT_SET, "Could not find RigDfu device! Peripheral not set.");
        errorReasons.put(INVALID_PARAMETER, "Firmware update error : Invalid parameter!");
        errorReasons.put(IMAGE_VALIDATION_FAILURE, "Failed to validate firmware image!");
        errorReasons.put(IMAGE_ACTIVATION_FAILURE, "Failed to activate firmware image!");
        errorReasons.put(PATCH_CURRENT_IMAGE_CRC_FAILURE,
                "CRC for the current firmware image does not match required CRC! " +
                        "Do you have the correct patch version?");
        errorReasons.put(POST_PATCH_IMAGE_CRC_FAILURE,
                "CRC for the updated firmware image does not match the required CRC! " +
                        "Do you have the correct patch version?");
        errorReasons.put(COULD_NOT_CONNECT, "Could not connect to RigDfu device!");
        errorReasons.put(UNKNOWN_ERROR, "An unknown error occured.");
        errorReasons.put(CONNECTION_FAILED, "RigDfu connection failure!");
        errorReasons.put(CONNECTION_TIMEOUT, "RigDfu connection time out!");
        errorReasons.put(DISCOVERY_TIMEOUT, "Discovery timeout. Could not find RigDfu device!");
        errorReasons.put(BOOTLOADER_DISCONNECT,
                "RigDfu disconnected! Could not complete firmware update.");
        errorReasons.put(FIRMWARE_UPDATE_CANCELLED, "Firmware update cancelled!");
    }

    /**
     *
     * @return A more detailed description of the error
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     *
     * @return The defined error code
     */
    public int getErrorCode() {
        return errorCode;
    }

    public RigDfuError(int errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     *
     * @param errorCode The defined error code
     * @return An instance of RigDfuError that contains the specified errorCode
     *         and its associated error message. A default error message is returned
     *         if the {@code errorCode} is invalid.
     */
    public static RigDfuError errorFromCode(int errorCode) {
        String message = "An unknown error occurred.";
        if (errorReasons.containsKey(errorCode)) {
            message = errorReasons.get(errorCode);
        }
        return new RigDfuError(errorCode, message);
    }
}
