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

    public static final int OPERATION_INVALID_STATE = 2;
    public static final int OPERATION_NOT_SUPPORTED = 3;
    public static final int OPERATION_DATA_SIZE_EXCEEDS_LIMIT = 4;
    public static final int OPERATION_OPERATION_FAILED = 6;
    public static final int IMAGE_VALIDATION_FAILURE = -6;
    public static final int IMAGE_ACTIVATION_FAILURE = -7;
    public static final int PATCH_CURRENT_IMAGE_CRC_FAILURE = -8;
    public static final int POST_PATCH_IMAGE_CRC_FAILURE = -9;
    public static final int CONNECTION_FAILED = -30;
    public static final int CONNECTION_TIMEOUT = -32;
    public static final int DISCOVERY_TIMEOUT = -30;

    private static final Map<Integer, String> errorReasons;

    static {
        errorReasons = new HashMap<>();
        //TODO : These "Operation" errors should probably make more sense
        errorReasons.put(OPERATION_INVALID_STATE, "Operation invalid state reached.");
        errorReasons.put(OPERATION_NOT_SUPPORTED, "Operation is not supported.");
        errorReasons.put(OPERATION_DATA_SIZE_EXCEEDS_LIMIT, "Operation data size exceeds limit.");
        errorReasons.put(OPERATION_OPERATION_FAILED, "Operation failed.");

        errorReasons.put(IMAGE_VALIDATION_FAILURE, "Failed to validate firmware image!");
        errorReasons.put(IMAGE_ACTIVATION_FAILURE, "Failed to activate firmware image!");
        //The image on the device before patching does not match the image the patch was started from.
        errorReasons.put(PATCH_CURRENT_IMAGE_CRC_FAILURE, "CRC for the current firmware image does not match required CRC! Do you have the correct patch version?");
        errorReasons.put(POST_PATCH_IMAGE_CRC_FAILURE, "CRC for the updated firmware image does not match the required CRC! Do you have the correct patch version?");
        errorReasons.put(CONNECTION_FAILED, "RigDfu connection failure!");
        errorReasons.put(CONNECTION_TIMEOUT, "RigDfu connection time out!");
        errorReasons.put(DISCOVERY_TIMEOUT, "Discovery timeout. Could not find RigDfu device!");
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
     * @return An instance of RigDfuError that contains the specified errorCode and its error message
     */
    public static RigDfuError errorFromCode(int errorCode) {
        String message = "An unknown error occured.";
        if (errorReasons.containsKey(errorCode)) {
            message = errorReasons.get(errorCode);
        }
        return new RigDfuError(errorCode, message);
    }
}
