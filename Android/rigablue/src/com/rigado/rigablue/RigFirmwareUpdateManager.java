package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothProfile;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 *  RigFirmwareUpdateManager.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * @author Eric Stutzenberger
 * @version 1.0
 */

/**
 * Enum for command codes sent to and received from the bootloader.
 */
enum DfuOpCodeEnum {
    DfuOpCode_Reserved_0,
    DfuOpCode_Start,
    DfuOpCode_Init,
    DfuOpCode_ReceiveFirmwareImage,
    DfuOpCode_ValidateFirmwareImage,
    DfuOpCode_ActivateFirmwareImage,
    DfuOpCode_SystemReset,
    DfuOpCode_Reserved_7,
    DfuOpCode_Reserved_8,
    DfuOpCode_EraseAndReset,
//    DfuOpCode_EraseSizeRequest, //removed because it is 10, and we need InitializePatch to be 10
    DfuOpCode_InitializePatch,
    DfuOpCode_ReceivePatchImage
}

/**
 * Enum for tracking the current state of the firmware update.
 */
enum FirmwareManagerStateEnum {
    /** Initial state, set in constructor and during cleanup */
    State_Init,
    // unused
    State_UserUnpluggedDeviceAwaitingReconnect,
    /** After updateFirmware is called, and before service discovery */
    State_DiscoverFirmwareServiceCharacteristics,
    // deprecated (erase size request)
    State_CheckEraseAfterUnplug,
    // unused
    State_TriggeredErase,
    // deprecated (erase size request)
    State_ReconnectAfterInitialFlashErase,
    // deprecated (erase size request)
    State_TransferringStmUpdateImage,
    // unused
    State_ActivatingStmUpdaterImage,
    // deprecated (erase size request)
    State_ReconnectAfterStmUpdate,
    // unused
    State_WaitReconnectAfterStmUpdateAndInvalidate,
    // deprecated (erase size request)
    State_ReconnectAfterStmUpdateFlashErase,
    /** Entered when ctrl point notifications have been enabled, and we're ready to send `DfuOpCode_Start` */
    State_TransferringRadioImage,
    /** Entered once transfer is done, but before validateFirmware() is called */
    State_FinishedRadioImageTransfer,

    /** Entered when firmware validation passes (from onCharacteristicChanged notification) */
    State_ImageValidationPassed,
    /** Entered when didWriteValueForControlPoint is called and state is State_FinishedRadioImageTransfer */
    State_ImageValidationWriteCompleted,
    /** Validation has succeeded and validation write has completed. Ready to activate Firmware. */
    // Note: This state is explicitly modeled to make it easy to trigger didFinishUpdate;
    // must go through ImageValidationPassed and ImageValidationWriteCompleted, but order is indeterminate
    State_ImageValidationWriteCompletedAndPassed,

    /** Entered when cancelUpdate() called */
    State_Cancelled
}

/**
 * This class manages the entire firmware update process based on the provided input images and
 * bootloader reset command.
 */
public class RigFirmwareUpdateManager implements IRigLeDiscoveryManagerObserver, IRigLeConnectionManagerObserver,
                                                IRigFirmwareUpdateServiceObserver {

    private static final int Response = 16; //unused in iOS
    private static final int PacketReceivedNotificationRequest = 8;
    private static final int NumberOfPackets = 1;

    private static final int PacketReceivedNotification = 17;
    private static final int ReceivedOpcode = 16;

    private static final int OPERATION_SUCCESS = 1;
    private static final int OPERATION_INVALID_STATE = 2; //Unused in iOS
    private static final int OPERATION_NOT_SUPPORTED = 3; //Unused in iOS
    private static final int OPERATION_DATA_SIZE_EXCEEDS_LIMIT = 4; //Unused in iOS
    private static final int OPERATION_CRC_ERROR = 5;
    private static final int OPERATION_OPERATION_FAILED = 6; //Unused in iOS
    private static final int OPERATION_PATCH_NEED_MORE_DATA = 7;
    private static final int OPERATION_PATCH_INPUT_IS_FULL = 8; //Unused in iOS

    private static final int BytesInOnePacket = 20;

    private static final int ImageStartPacketIndex = 0; //unused in iOS
    private static final int ImageStartPacketSize = 12;
    private static final int ImageInitPacketIndex = ImageStartPacketSize;
    private static final int ImageInitPacketSize = 32;

    private static final int PatchInitPacketIndex = ImageStartPacketSize + ImageInitPacketSize;
    private static final int PatchInitPacketSize = 12;

    private static final int ImageSecureDataStart = ImageInitPacketIndex + ImageInitPacketSize;


    private RigFirmwareUpdateService mFirmwareUpdateService;
    private FirmwareManagerStateEnum mState;

    //Removed from DfuOpOrdinal because we need InitializePatch to be 10
    private final static int ERASE_SIZE_REQUEST = 10;

    /**
     * If the first 16 bytes matches this key, it is a patch update.
     */
    private final static byte[] patchKey = {(byte)0xac, (byte)0xb3, 0x37, (byte) 0xe8, (byte) 0xd0, (byte) 0xeb,
            0x40, (byte)0x90, (byte)0xa4, (byte)0xf3, (byte)0xbb, (byte)0x85, 0x7a, 0x5b, 0x2a, (byte)0xf6 };

    /**
     * Size of patch key
     */
    private final static int PATCH_KEY_SIZE = 16;

    /**
     * Array to hold the firmware image being sent to the bootloader
     */
    byte [] mStartImage;

    /**
     * Array to hold the final firmware image after the patch check
     */
    byte [] mImage;

    /**
     * The size of the firmware image.
     */
    int mImageSize;

    /**
     * State variable which is set to true once the firmware size has been successfully transmitted
     * to the bootloader.
     */
    boolean mIsFileSizeWritten;

    /**
     * State variable which is set to true once the init packet has been sent to the bootloader.
     */
    boolean mIsInitPacketSent;

    /**
     * State variable which is set to true once packet notifications have been enabled.  Note:
     * Packet Notifications are a specific response that is sent AS a notification from the
     * bootloader.  After a configurable number of data packets have been sent to the bootloader,
     * the bootloader will send a packet notification containing the total number of bytes sent
     * so far.  This allows the appliation to determine if all of the data sent thus far has been
     * received by the bootloader.
     */
    boolean mIsPacketNotificationEnabled;

    /**
     * State variable set to true once the firmware image is being sent to the bootloader.
     */
    boolean mIsReceivingFirmwareImage;

    /**
     * State variable set to true when the update manager is about to send the last firmware image
     * packet.
     */
    boolean mIsLastPacket;


    boolean mShouldStopSendingPackets;

    /**
     * This state is no longer used and will eventually be removed.
     */
    boolean mShouldWaitForErasedSize;

    /**
     * State variable set to true when the activate firmware command has been successfully sent.
     */
    boolean mDidActivateFirmware;

    /**
     * This state is no longer used and will eventually be removed.
     */
    boolean mDidForceEraseAfterStmUpdateImageRan;

    /**
     * State variable which is set in the method firmwareUpdate
     */
    boolean isPatchUpdate;

    /**
     * State variable which is set to true once the patch init packet has been sent to the bootloader.
     */
    boolean mIsPatchInitPacketSent;

    /**
     * The total number of firmware packets to be sent.
     */
    int mTotalPackets;

    /**
     * The current packet number.
     */
    int mPacketNumber;

    /**
     * The total bytes sent so far.
     */
    int mTotalBytesSent;

    /**
     * This variable is no longer used and will be removed.
     */
    int mTotalBytesErased;

    /**
     * The size of the last packet that will be sent.
     */
    int mLastPacketSize;

    /**
     * The observer object for this class.
     */
    private IRigFirmwareUpdateManagerObserver mObserver;

    /**
     * #RigFirmwareUpdateManager needs to take over the roll of the connection observer since
     * invoking the bootloader and performing firmware updates requires a disconnect and
     * reconnection to the device being updated.  Once the firmware update is completed (or if it
     * fails), the previous connection observer will be restored.
     */
    IRigLeConnectionManagerObserver mOldConnectionObserver;

    /**
     * The device being updated.
     */
    RigLeBaseDevice mUpdateDevice;

    /**
     * The address of the device when not in bootloader mode.
     */
    String mInitialDeviceAddress;

    /**
     * Internal state variable denoting a cancel has been issued to the bootloader.
     */
    boolean mDidSendCancel;

    /**
     * Creates a new firmware update manager.
     */
    public RigFirmwareUpdateManager() {
    	initStateVariables();
    }


    /**
     * Performs a firmware update.
     *
     * @param device The device to update
     * @param firmwareImage The firmware image to send to the device
     * @param activateCharacteristic The #BluetoothGattCharacteristic to which the
     *                               <code>activate command</code> is sent in order to cause the
     *                               bootloader to start
     * @param activateCommand The bootloader activation command to write to the
     *                        activateCharacteristic
     * @return Returns true if the firmware update started successfully, false otherwise
     */
    public boolean updateFirmware(RigLeBaseDevice device,
                                  InputStream firmwareImage,
                                  BluetoothGattCharacteristic activateCharacteristic,
                                  byte[] activateCommand) {
        RigLog.i("__RigFirmwareUpdateManager.updateFirmware__");

        mFirmwareUpdateService = new RigFirmwareUpdateService();
        mFirmwareUpdateService.setObserver(this);

        if (device == null || firmwareImage == null || activateCharacteristic == null || activateCommand == null) {
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.INVALID_PARAMETER));
            return false;
        }

        mUpdateDevice = device;

        try {
            mImageSize = firmwareImage.available();
            mStartImage = new byte[mImageSize];
            firmwareImage.read(mStartImage);
        } catch(IOException e) {
            RigLog.e("IOException occurred while reading binary image data!");
        }

        if (mStartImage.length < PATCH_KEY_SIZE) {
            RigLog.d("Received invalid binary!");
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.INVALID_PARAMETER));
            return false;
        }

        isPatchUpdate = isPatchUpdate(mStartImage);

        if(isPatchUpdate) {
            //chop off the patch key -- first 16 bytes
            mImageSize -= PATCH_KEY_SIZE;
            mImage = new byte[mImageSize];
            System.arraycopy(mStartImage, PATCH_KEY_SIZE, mImage, 0, mImageSize);
        } else {
            //nothing to do here!
            mImage = mStartImage;
        }

        mFirmwareUpdateService.setShouldReconnectState(true);
        mState = FirmwareManagerStateEnum.State_DiscoverFirmwareServiceCharacteristics;

        //TODO: Change this to be based on the available services and not the name
        if(mUpdateDevice.getName() != null && mUpdateDevice.getName().equals("RigDfu")) {
            mFirmwareUpdateService.setDevice(mUpdateDevice);
            if(RigCoreBluetooth.getInstance().getDeviceConnectionState(mUpdateDevice.getBluetoothDevice()) ==
                    BluetoothProfile.STATE_CONNECTED) {
                if(!mUpdateDevice.isDiscoveryComplete()) {
                    mFirmwareUpdateService.triggerServiceDiscovery();
                } else {
                    if(mFirmwareUpdateService.getServiceAndCharacteristics()) {
                        mFirmwareUpdateService.enableControlPointNotifications();
                    } else {
                        RigLog.e("Failed when attempting to find dfu service and characteristics");
                        return false;
                    }
                }
            } else {
                mFirmwareUpdateService.connectDevice();
            }
        } else {
            sendEnterBootloaderCommand(activateCharacteristic, activateCommand);
            return true;
        }

        return true;
    }

    /**
     * Utility method to check the observer state before sending a status update
     *
     * @param status The current status of the firmware update
     */
    private void updateStatus(final String status) {
        if (mObserver != null) {
            mObserver.updateStatus(status, 0);
        }
    }

    /**
     * Cancels an in progress firmware update. Sets {@code mState} to
     * {@link FirmwareManagerStateEnum#State_Cancelled} and prevents reconnection attempts.
     *
     * This method should only be called after a successful connection to the bootloader. We send a
     * reset command to potentially reset any long-running bootloader advertising process.
     *
     * However, if RigDfu has been discovered, and a connection is in progress, but not completed,
     * it may still continue to connect in the background. To prevent this, store a reference to the
     * bootloader {@link RigAvailableDeviceData} and cancel the pending connection before calling
     * {@link #cancelUpdate()}. See the below example:
     *
     * <code>
     *     mConnectionManager.cancelConnection(mRigDfuAvailableDeviceData);
     * </code>
     *
     * The reset (or cancelled connection) will trigger {@link #didDisconnectDevice(BluetoothDevice)}
     * or {@link #didDisconnectPeripheral()} depending on connection state at the time of the cancel.
     * Either callback will pass the correct error code to {@link #handleUpdateError(RigDfuError)}.
     *
     */
    public void cancelUpdate() {
        RigLog.e("__cancelUpdate__");

        updateStatus("Cancelling...");

        mState = FirmwareManagerStateEnum.State_Cancelled;

        if (mDiscoveryManager.isDiscoveryRunning()) {
            mDiscoveryManager.stopDiscoveringDevices();
        }

        mFirmwareUpdateService.setShouldAlwaysReconnectState(false);
        mFirmwareUpdateService.setShouldReconnectState(false);

        byte [] data = { (byte)DfuOpCodeEnum.DfuOpCode_SystemReset.ordinal() };
        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(data);

        if (error != null) {
            RigLog.w("Unable to cancel firmware update!");
            handleUpdateError(error);
        }
        mDidSendCancel = true;
    }


    /**
     * Initializes the state of the firmware update manager object.
     */
    private void initStateVariables() {
        RigLog.i("__initStateVariables__");
    	mIsFileSizeWritten = false;
    	mIsInitPacketSent = false;
    	mIsPacketNotificationEnabled = false;
    	mIsReceivingFirmwareImage = false;
    	mIsLastPacket = false;
    	mShouldStopSendingPackets = false;
    	mDidForceEraseAfterStmUpdateImageRan = false;

    	mObserver = null;
    	mState = FirmwareManagerStateEnum.State_Init;
    	mImageSize = 0;
    	mImage = null;
    }

    /**
     * Sets the observer for this instance
     * @param observer The observer to set
     */
    public void setObserver(IRigFirmwareUpdateManagerObserver observer) {
        mObserver = observer;
    }

    /**
     * Detects whether the supplied firmware image is a patch
     *
     * @param firmwareImage the firmware image passed to the bootloader
     * @return true if patch update
     */
    private boolean isPatchUpdate(byte[] firmwareImage) {
        boolean isPatch = false;
        final byte[] maybePatchKey = new byte[PATCH_KEY_SIZE];
        System.arraycopy(firmwareImage, 0, maybePatchKey, 0, PATCH_KEY_SIZE);

        if(Arrays.equals(patchKey, maybePatchKey)) {
            isPatch = true;
        }
        RigLog.i("__RigFirmwareUpdateManager.isPatchUpdate__ :" + isPatch);
        return isPatch;
    }

    /**
     * Max time in milliseconds to discover RigDfu
     */
    private final static int MAX_RIGDFU_DISCOVERY_TIMEOUT = 20000;
    private RigLeDiscoveryManager mDiscoveryManager;

    /**
     * Sends the command to put the device in to bootloader mode.
     *
     * @param characteristic The characteristic which accepts the bootloader activate command
     * @param command The command which causes the device to enter the bootloader
     */
    private void sendEnterBootloaderCommand(BluetoothGattCharacteristic characteristic, byte [] command) {
        RigLog.d("__RigFirmwareUpdateManager.sendEnterBootloaderCommand__");

        mDiscoveryManager = RigLeDiscoveryManager.getInstance();
        mDiscoveryManager.stopDiscoveringDevices();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String[] dfuServiceUuidStrings = mFirmwareUpdateService.getDfuServiceUuidStrings();
        RigDeviceRequest dr = new RigDeviceRequest(dfuServiceUuidStrings, MAX_RIGDFU_DISCOVERY_TIMEOUT);
        dr.setObserver(this);
        mDiscoveryManager.startDiscoverDevices(dr);

        updateStatus("Searching for updater service...");

        mFirmwareUpdateService.setInitialNonBootloaderDevice(mUpdateDevice);
        mInitialDeviceAddress = mUpdateDevice.getBluetoothDevice().getAddress();
        mOldConnectionObserver = RigLeConnectionManager.getInstance().getObserver();
        RigLeConnectionManager.getInstance().setObserver(this);

        RigLog.d("Send enter bootloader command");

        mUpdateDevice.writeCharacteristic(characteristic, command);
    }

    /**
     * This method cleans up the state of the firmware update manager in case of a failed
     * firmware update.
     */
    private void cleanUp() {
        RigLog.d("__RigFirmwareUpdateManager.cleanUp__");
    	RigLeConnectionManager.getInstance().setObserver(mOldConnectionObserver);

    	if(mUpdateDevice != null) {
    		if(RigCoreBluetooth.getInstance().getDeviceConnectionState(mUpdateDevice.getBluetoothDevice()) ==
                    BluetoothProfile.STATE_CONNECTED) {
    			RigLeConnectionManager.getInstance().disconnectDevice(mUpdateDevice);
    		}
    	}

    	initStateVariables();
    }

    /**
     * Determines the size of the firmware update image.  For the secure bootloader, the true
     * image size is encoded within the image data and is not simply the size of the array.
     *
     * @return The size of the update in bytes
     */
    private int getImageSize() {
        RigLog.i("__RigFirmwareUpdateManager.getImageSize__");
    	int size = mImageSize;
    	if(mFirmwareUpdateService.isSecureDfu()) {
    		size -= (ImageStartPacketSize + ImageInitPacketSize);
            if(isPatchUpdate) {
                size -= PatchInitPacketSize;
            }
    	}
    	return size;
    }

    /**
     * Determines the starting index of the firmware update image.
     *
     * @return The image start index
     */
    private int getImageStart() {
        RigLog.i("__RigFirmwareUpdateManager.getImageStart__");
        final int imageStart;
        if(isPatchUpdate) {
            imageStart = ImageSecureDataStart + PatchInitPacketSize;
        } else {
            imageStart = ImageSecureDataStart;
        }

        return imageStart;
    }

    /**
     * Resets all state flags
     */
    private void resetFlags() {
        RigLog.d("__RigFirmwareUpdateManager.resetFlags__");
        mIsFileSizeWritten = false;
        mIsPacketNotificationEnabled = false;
        mIsReceivingFirmwareImage = false;
        mPacketNumber = 0;
        mTotalBytesSent = 0;
        mIsLastPacket = false;
    }

    /**
     * Triggers a service discovery
     */
    private void updateDeviceAndTriggerDiscovery() {
    	mFirmwareUpdateService.setShouldReconnectState(true);
    	mFirmwareUpdateService.triggerServiceDiscovery();
    }

    /**
     * Sends the size of the firmware image to the bootloader
     */
    private void writeFileSize() {
        RigLog.d("__RigFirmwareUpdateManager.writeFileSize__");

        byte [] data;

        if (mFirmwareUpdateService.isSecureDfu()) {
            data = new byte[ImageStartPacketSize];
            System.arraycopy(mImage, 0, data, 0, ImageStartPacketSize);
        } else {
            data = new byte[4];
            data[0] = (byte)(mImageSize & 0xFF);
            data[1] = (byte)((mImageSize >> 8) & 0xFF);
            data[2] = (byte)((mImageSize >> 16) & 0xFF);
            data[3] = (byte)((mImageSize >> 24) & 0xFF);
        }

        final RigDfuError error = mFirmwareUpdateService.writeToPacketCharacteristic(data);
        if (error != null) {
            RigLog.w("Failed to write image size to the packet characteristic!");
            handleUpdateError(error);
            return;
        }

        updateStatus("Writing device update size");

    }

    /**
     * Helper function to enable packet notifications
     */
    private void enablePacketNotification() {
        RigLog.d("__RigFirmwareUpdateManager.enablePacketNotification__");
        byte [] data = { PacketReceivedNotificationRequest, NumberOfPackets, 0 };

        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(data);
        if (error != null) {
            RigLog.w("Failed to write to the control point. Could not enable packet notifications!");
            handleUpdateError(error);
            return;
        }

        updateStatus("Enabling packet notifications");

    }

    /**
     * Sends the init packet to the secure bootloader.  The init packet contains information
     * regarding the encryption of the firmware image and which application binaries are being
     * sent.  Typically, only the application binary is sent.
     */
    private void sendInitPacket() {
    	RigLog.d("__RigFirmwareUpdateManager.sendInitPacket__");
    	byte [] initPacketOne = new byte[ImageInitPacketSize/2];
    	byte [] initPacketTwo = new byte[ImageInitPacketSize/2];

    	System.arraycopy(mImage, ImageInitPacketIndex, initPacketOne, 0, ImageInitPacketSize/2);
    	System.arraycopy(mImage, ImageInitPacketIndex + (ImageInitPacketSize/2), initPacketTwo, 0, ImageInitPacketSize/2);

    	final RigDfuError error = mFirmwareUpdateService.writeToPacketCharacteristic(initPacketOne);
        if (error != null) {
            RigLog.w("Failed to write first init packet!");
            handleUpdateError(error);
            return;
        }

    	try {
    		Thread.sleep(100);
    	} catch (Exception e) {
            e.printStackTrace();
    	}

    	final RigDfuError maybeError = mFirmwareUpdateService
                .writeToPacketCharacteristic(initPacketTwo);
        if (maybeError != null) {
            RigLog.w("Failed to write second init packet!");
            handleUpdateError(maybeError);
        }
    }
    /**
     * Sends the patch init packet to the secure bootloader.  The init packet contains information
     * regarding the encryption of the firmware image and which application binaries are being
     * sent.  Typically, only the application binary is sent. The patch init packet also
     * contains a 128-bit key to identify it as a patch.
     */
    private void sendPatchInitPacket() {
        RigLog.i("__RigFirmwareUpdateManager.sendPatchInitPacket__");
        byte[] patchInitPacket = new byte[PatchInitPacketSize];

        System.arraycopy(mImage, PatchInitPacketIndex, patchInitPacket, 0, PatchInitPacketSize);

        final RigDfuError error =
                mFirmwareUpdateService.writeToPacketCharacteristic(patchInitPacket);
        if (error != null) {
            RigLog.w("Failed to write patch init packet!");
            handleUpdateError(error);
        }
    }

    /**
     * Commands the bootloader to prepare for receiving the firmware image.
     */
    private void receiveFirmwareImage() {
        RigLog.d("__RigFirmwareUpdateManager.receiveFirmwareImage__");
        byte [] data = { (byte)DfuOpCodeEnum.DfuOpCode_ReceiveFirmwareImage.ordinal() };

        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(data);
        if (error != null) {
            RigLog.w("Failed to initialize firmware image transfer!");
            handleUpdateError(error);
        }
    }

    /**
     * Starts the transfer of the firmware image data.
     */
    private void startUploadingFile() {
        RigLog.d("__RigFirmwareUpdateManager.startUploadingFile__");
        int size = getImageSize();
        mTotalPackets = (size / BytesInOnePacket);
        if((size % BytesInOnePacket) != 0) {
            mTotalPackets++;
        }

        determineLastPacketSize();

        updateStatus("Transferring New Device Software");

        final RigDfuError error = sendPacket();
        if (error != null) {
            RigLog.w("Failed to start transfer of image data!");
            handleUpdateError(error);
        }
    }

    /**
     * Helper function to determine the size of the last packet that will be sent.
     */
    private void determineLastPacketSize() {
    	int imageSizeLocal = getImageSize();
    	if((imageSizeLocal % BytesInOnePacket) == 0) {
    		mLastPacketSize = BytesInOnePacket;
    	} else {
    		mLastPacketSize = (imageSizeLocal - ((mTotalPackets - 1) * BytesInOnePacket));
    	}
    }

    /**
     * Sends one packet of data to the Dfu. If the packet being sent is the last packet, then the
     * size is based on the size calculated in the call to determineLastPacketSize.
     */
    private RigDfuError sendPacket() {
        RigLog.d("__RigFirmwareUpdateManager.sendPacket__");

        /**
         * If we fail to read a characteristic, we assume the device is in an invalid state and
         * call {@link IRigCoreListener#onActionGattFail(BluetoothDevice)}, which forces a
         * disconnect event. The disconnect event calls {@code cleanUp},
         * {@code handleUpdateError), and {@code initStateVariables}. All variables are reset.
         * However, it can take several seconds for the device to actually disconnect.
         * Meanwhile, we might still be receiving {@link IRigCoreListener} callbacks. This
         * check prevents NPEs caused by trying to get the {@code mImage} size in response to a
         * notification received after a forced disconnect event.
         */
        if (mState.ordinal() < FirmwareManagerStateEnum.State_TransferringRadioImage.ordinal()) {
            RigLog.e("Exiting send packet operation! State is < State_TransferringRadioImage.");
            return RigDfuError.errorFromCode(RigDfuError.BOOTLOADER_DISCONNECT);
        }

        mPacketNumber++;
        byte [] packet;
        int packetSize = BytesInOnePacket;

        if(mPacketNumber == mTotalPackets) {
            RigLog.i("Sending last packet: " + mPacketNumber);
            mIsLastPacket = true;

            packetSize = mLastPacketSize;
        } else {
            RigLog.i("Sending packet: " + mPacketNumber + "/" + mTotalPackets);
        }

        packet = new byte[packetSize];
        int index;
        final int imageStart = getImageStart();
        if(mFirmwareUpdateService.isSecureDfu()) {
        	index = imageStart + (mPacketNumber - 1) * BytesInOnePacket;
        } else {
        	index = (mPacketNumber - 1) * BytesInOnePacket;
        }

        System.arraycopy(mImage, index, packet, 0, packetSize);
        final RigDfuError error =
                mFirmwareUpdateService.writeToPacketCharacteristic(packet);

        return error;
    }

    /**
     * Instructs the bootloader to validate the firmware image that was sent.  This is called
     * after the last packet has been received by the bootloader.
     */
    private void validateFirmware() {
        mState = FirmwareManagerStateEnum.State_FinishedRadioImageTransfer;

        RigLog.d("__RigFirmwareUpdateManager.validateFirmware__");
        byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_ValidateFirmwareImage.ordinal() };

        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
        if (error != null) {
            RigLog.w("Failed to start firmware validation!");
            handleUpdateError(error);
            return;
        }

        updateStatus("Validating updated device software");
    }

    /**
     * Once the firmware is validated AND write has completed, the firmware transfer is considered successful
     * and the activation command is sent to the DFU.
     *
     * We model the "completed & passed" state explicitly rather than relying on the write queue to schedule
     * our activation write in order to have an easy way to determine when the update has finished.
     */
    private void finishValidation () {
        RigLog.d("__RigFirmwareUpdateManager.finishValidation__");
        updateStatus("Device software validated successfully!");
        mState = FirmwareManagerStateEnum.State_ImageValidationWriteCompletedAndPassed;
        activateFirmware();
    }

    /**
     * Instructs the bootloader to active the new firmware image (i.e. run it) and then completes
     * the update by reassigning the connection observer.
     */
    private void activateFirmware() {
        RigLog.d("__RigFirmwareUpdateManager.activateFirmware__");

        byte[] cmd = {(byte) DfuOpCodeEnum.DfuOpCode_ActivateFirmwareImage.ordinal()};
        if (mState == FirmwareManagerStateEnum.State_ActivatingStmUpdaterImage) {
            mDidActivateFirmware = true;
        }

        /* Set these fields here as the disconnect may be reported during the onWriteValue callback
         * and then a reconnect will be attempted. */
        mFirmwareUpdateService.setShouldReconnectState(false);
        mFirmwareUpdateService.setShouldAlwaysReconnectState(false);
        mFirmwareUpdateService.completeUpdate();

        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
        if (error != null) {
            RigLog.w("Failed to start activating device software!");
            handleUpdateError(error);
            return;
        }

        updateStatus("Activating device software");
    }

    /**
     * Called when a firmware update fails or is cancelled. Prevents reconnection
     * attempts, ensures disconnection from the device, resets state variables,
     * and sends an error message to the original observer.
     *
     * @param error An instance of #RigDfuError
     */
    private void handleUpdateError(RigDfuError error) {
        //Set to null when cleanUpAfterFailure is called, store a reference here
        IRigFirmwareUpdateManagerObserver o = mObserver;

        mFirmwareUpdateService.setShouldAlwaysReconnectState(false);
        mFirmwareUpdateService.setShouldReconnectState(false);

        this.cleanUp();

        if(o != null) {
            o.updateFailed(error);
        }
    }

    /**
     * Called when the bootloader peripheral is connected.
     */
    @Override
    public void didConnectPeripheral() {
    	mFirmwareUpdateService.triggerServiceDiscovery();
    }

    /**
     * Called when the dfu service and characteristics have been discovered.
     */
	@Override
    public void didDiscoverCharacteristicsForDFUService() {
        RigLog.d("__RigFirmwareUpdateManager.didDiscoverCharacteristicsForDFUService__");

        if(mState == FirmwareManagerStateEnum.State_TransferringRadioImage) {
        	mState = FirmwareManagerStateEnum.State_Init;
        	mIsFileSizeWritten = false;
        	mIsInitPacketSent = false;
        	mIsPacketNotificationEnabled = false;
        	mIsReceivingFirmwareImage = false;
        	updateProgress(0);
        	mPacketNumber = 0;
        }

        mFirmwareUpdateService.enableControlPointNotifications();
    }

    /**
     * Called when notifications for the control point have been successfully enabled.
     */
    @Override
    public void didEnableControlPointNotifications() {
        RigLog.d("__RigFirmwareUpdateManager.didEnableControlPointNotifications__");

        byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_Start.ordinal() };

        mShouldWaitForErasedSize = false;
        mState = FirmwareManagerStateEnum.State_TransferringRadioImage;
        final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
        if (error != null) {
            RigLog.w("Failed to write start DFU command to control point!");
            handleUpdateError(error);
            return;
        }

        updateStatus("Initializing Device Firmware Update");
    }

    /**
     * Called anytime a characteristic has been successfully written.
     */
    @Override
    public void didWriteValueForControlPoint() {
        RigLog.d("__RigFirmwareUpdateManager.didWriteValueForControlPoint__");

        //This check ensures none of the above is performed until after we successfully erase the flash
        //Legacy code for old bootloader versions.
        if (mShouldWaitForErasedSize) {
            return;
        }

        //If the firmware update was cancelled, exit the write operation. We are
        //waiting for a reset and disconnect callback.
        if (mState == FirmwareManagerStateEnum.State_Cancelled && mDidSendCancel) {
            return;
        }

        if (mState == FirmwareManagerStateEnum.State_FinishedRadioImageTransfer) {
            mState = FirmwareManagerStateEnum.State_ImageValidationWriteCompleted;
        } else if (mState == FirmwareManagerStateEnum.State_ImageValidationPassed) {
            finishValidation();
        } else if (mState == FirmwareManagerStateEnum.State_ImageValidationWriteCompletedAndPassed) {
            try {
                Thread.sleep(2000);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (mObserver != null) {
                mObserver.didFinishUpdate();
            }
        }

        /*
         * This functionality behaves in tandem with the commands being written
         * to the control point from didUpdateValueForControlPoint
         */
        if (!mIsFileSizeWritten) {
            writeFileSize();
        } else if (!mIsInitPacketSent && mFirmwareUpdateService.isSecureDfu()) {
            sendInitPacket();
        } else if (!mIsPatchInitPacketSent && isPatchUpdate){
            sendPatchInitPacket();
        } else if(!mIsPacketNotificationEnabled && !isPatchUpdate) {
            mIsPacketNotificationEnabled = true;
            receiveFirmwareImage();
        } else if(!mIsReceivingFirmwareImage) {
            mIsReceivingFirmwareImage = true;
            startUploadingFile();
        }
    }

    /**
     * Called when the bootloader peripheral disconnects.  If the firmware update was successful,
     * then the connection observer will be reassigned and this method will not be invoked.
     *
     * We check state here, because disconnection could be caused by cancelling an in progress update.
     * However, if it is not `State_Cancelled`, then we know the bootloader connection failed.
     */
    @Override
    public void didDisconnectPeripheral() {
        RigLog.d("__RigFirmwareUpdateManager.didDisconnectDevice__");
        if(mState == FirmwareManagerStateEnum.State_Cancelled) {
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.FIRMWARE_UPDATE_CANCELLED));
        } else {
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.BOOTLOADER_DISCONNECT));
        }
    }

    /**
     * Called when a notification is received on the control point.
     *
     * @param value The updated control point value
     */
    @Override
    public void didUpdateValueForControlPoint(byte [] value) {
        RigLog.d("__RigFirmwareUpdateManager.didUpdateValueForControlPoint__");

        byte opCode = value[0];
        byte request = value[1];

        if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_Start.ordinal()) {
            if(value[2] == OPERATION_SUCCESS) {
                /**
                 * This is received after sending the size of the firmware image to the packet characteristic
                 * the device has been properly erased by the Dfu. Here, we mark the fact that the firmware
                 * image size has been sent and enable notifications for packets. This will cause a
                 * didWriteValueToCharacteristic call to be generated by CoreBluetooth which will then cause
                 * the firmware image transfer to begin.
                 */
                mIsFileSizeWritten = true;
                if(mFirmwareUpdateService.isSecureDfu()) {
                	byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_Init.ordinal() };
                	final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
                    if (error != null) {
                        RigLog.w("Failed to write DFU initialization to control point!");
                        handleUpdateError(error);
                    }
                } else {
                	enablePacketNotification();
                }
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_Init.ordinal()) {
        	if(value[2] == OPERATION_SUCCESS) {
                //This is received after sending the patch initialization data. If the update is not
                //a patch, then this step is skipped.
        		mIsInitPacketSent = true;
                if(isPatchUpdate) {
                    final byte[] cmd = {(byte) DfuOpCodeEnum.DfuOpCode_InitializePatch.ordinal()};
                    final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
                    if (error != null) {
                        RigLog.w("Failed to write initialization start to control point!");
                        handleUpdateError(error);
                    }
                } else {
                    enablePacketNotification();
                }
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_InitializePatch.ordinal()) {
            if(value[2] == OPERATION_SUCCESS) {
                mIsPatchInitPacketSent = true;
                //At this point, the patching process and the normal update process diverge. In the normal
                //case, the packet notifications would be enabled. Instead, the page image transfer starts.
                final byte[] cmd = {(byte) DfuOpCodeEnum.DfuOpCode_ReceivePatchImage.ordinal()};
                final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
                if (error != null) {
                    RigLog.w("Failed to write patch initialization to control point!");
                    handleUpdateError(error);
                }
            } else if(value[2] == OPERATION_CRC_ERROR) {
                RigLog.e("CRC on patch initialization!");
                handleUpdateError(RigDfuError.errorFromCode(RigDfuError.PATCH_CURRENT_IMAGE_CRC_FAILURE));
            } else {
                RigLog.e("Unexpected error during patch initialization!");
                handleUpdateError(RigDfuError.errorFromCode(RigDfuError.UNKNOWN_ERROR));
            }

        } else if(opCode == PacketReceivedNotification) {
            //This is sent every time a packet is successfully received by the Dfu. This provides the app
            //a way of knowing that each packet has been received and the total size that has been
            //transferred thus far.
            mTotalBytesSent = ((value[1] & 0xFF) + ((value[2] & 0xFF) << 8) + ((value[3] & 0xFF) << 16) + ((value[4] & 0xFF) << 24));

            float progress = (float)mTotalBytesSent / (float)getImageSize();
            updateProgress(progress);

            //If we haven't sent the last packet yet, then keep sending packets. Once sent, we will notify
            //the app that the firmware image has been fully transferred.
            if(!mIsLastPacket && !mShouldStopSendingPackets) {
                // TODO: error handling in sendPacket()
                if((mTotalBytesSent / 20) != mPacketNumber) {
                    RigLog.e("Data consistency error!!!!");
                }
                final RigDfuError error = sendPacket();
                if (error != null) {
                    RigLog.w("Failed to send packet!");
                    handleUpdateError(error);
                }
            } else {
                RigLog.i("Last packet notification received.");
                updateProgress(1.0f);
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_ReceiveFirmwareImage.ordinal()) {
            if(value[2] == OPERATION_SUCCESS) {
                RigLog.i("Firmware transfer successful");
                updateStatus("Firmware transfer successful.  Validating...");
                validateFirmware();
            } else {
                RigLog.e("Error during firmware image transfer " + value[2]);
                handleUpdateError(RigDfuError.errorFromCode(RigDfuError.IMAGE_VALIDATION_FAILURE));
            }
        } else if (opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_ReceivePatchImage.ordinal()) {
            if(value[2] == OPERATION_PATCH_NEED_MORE_DATA) {
                final float progress =  ((float) (mPacketNumber * 20) / (float) getImageSize());
                updateProgress(progress);
                if(!mShouldStopSendingPackets) {

                    final RigDfuError error = sendPacket();
                    if (error != null) {
                        RigLog.w("Failed to send packet!");
                        handleUpdateError(error);
                    }
                }
            } else if(value[2] == OPERATION_SUCCESS) {
                updateProgress(1.0f);
                updateStatus("Successfully Transferred Software. Validating...");
                validateFirmware();
            }

        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_ValidateFirmwareImage.ordinal()) {
            if(value[2] == OPERATION_SUCCESS) {
                // Validation result comes from a notification. However, we may not have yet received a callback indicating
                // that the validation write has completed.
                if (mState == FirmwareManagerStateEnum.State_ImageValidationWriteCompleted) {
                    //finishValidation calls activateFirmware, which handles any potential RigDfuError
                    finishValidation();
                } else {
                    mState = FirmwareManagerStateEnum.State_ImageValidationPassed;
                }
            } else if(value[2] == OPERATION_CRC_ERROR) {
                RigLog.w("CRC Failure on Validation!");
                handleUpdateError(RigDfuError.errorFromCode(RigDfuError.POST_PATCH_IMAGE_CRC_FAILURE));
            } else {
                RigLog.w("Error occured during firmware validation!");
                handleUpdateError(RigDfuError.errorFromCode(RigDfuError.IMAGE_VALIDATION_FAILURE));

            }
        }  else if(opCode == ReceivedOpcode && request == (byte)ERASE_SIZE_REQUEST) {
            if(value[2] == OPERATION_SUCCESS) {
                /** NOTE: (iOS note)
                    This is not used for the dual bank bootloader.  Also, as of S110 7.0, it is not necessary to disable the
                    radio to read/write to/from the internal flash of the part.
                    This message is sent by the DFU after an erase size request.  Initially, if not erased, the erase
                    size request will return 0.  This will trigger the firmware update manager to send an erase and
                    reset command to the device which will then cause the device to disconnect and perform the
                    erase.  Once that is complete, the update manager will reconnect, perform discovery, and then send
                    another erase size request.  At that point, the erase size should be greater than the size of the
                    image to be sent.  If this is the case, then the firmware update is started by sending the DFU Start
                    command.  Once this command is written successfully, the didWriteValueToCharacteristic callback will
                    cause the firmware image size to be written to the device.  Once received, this will trigger the DFU
                    to send the DFU Start response operation code and firmware transfer will begin.  See the first if
                    condition in this function.
                 */

            	/* NOTE: (Android note)
            	 * This is no longer used for the dual bank bootloader.  The entire bank does not need to be
            	 * erased to perform a firmware update.  In additional, as of S110 7.0, it is no longer necessary to
            	 * disable the radio when reading/write to the internal flash of the nRF.  This code is left for reference
            	 * and backwards compatibility with old bootloaders.
            	 */
                mTotalBytesErased = (value[3] + (value[4] << 8) + (value[5] << 16) + (value[6] << 24)) & 0xFFFFFFFF;
                if(mTotalBytesErased < mImageSize) {
                    byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_EraseAndReset.ordinal() };
                    if(mState == FirmwareManagerStateEnum.State_CheckEraseAfterUnplug) {
                        mState = FirmwareManagerStateEnum.State_ReconnectAfterInitialFlashErase;
                    } else if(mState == FirmwareManagerStateEnum.State_ReconnectAfterStmUpdate) {
                        mState = FirmwareManagerStateEnum.State_ReconnectAfterStmUpdateFlashErase;
                    }
                    mFirmwareUpdateService.setShouldReconnectState(true);
                    final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
                    if (error != null) {
                        RigLog.w("Failed to initialize erase size request!");
                        handleUpdateError(error);
                    }
                } else {
                    //Device already erased, continue with the firmware update
                    byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_Start.ordinal() };
                    mShouldWaitForErasedSize = false;
                    if(mState == FirmwareManagerStateEnum.State_CheckEraseAfterUnplug) {
                        mState = FirmwareManagerStateEnum.State_TransferringStmUpdateImage;
                    }
                   final RigDfuError error = mFirmwareUpdateService.writeToControlPoint(cmd);
                    if (error != null) {
                        RigLog.w("Failed to start firmware update image transfer!");
                        handleUpdateError(error);
                    }
                }
            }
        }
    }

    /**
     * Sends the current progress of the firmware update to the
     * #IRigFirmwareUpdateManagerObserver if assigned.
     *
     * @param progress The current progress
     */
    private void updateProgress(float progress) {
        int progressPercentage = (int)(progress * 100);
        if(mObserver!=null) {
            mObserver.updateProgress(progressPercentage);
        }
    }

    /* IRigLeDiscoveryManagerObserver methods */

    /**
     * Called when the bootloader device has been discovered.
     *
     * @param device The available device information for the discovered device
     */

    @Override
    public void didDiscoverDevice(RigAvailableDeviceData device) {
        RigLog.d("__RigFirmwareUpdateManager.didDiscoverDevice__");
        RigLog.d("Found dfu device! " + device.toString());
        RigLeDiscoveryManager.getInstance().stopDiscoveringDevices();
        RigLeConnectionManager.getInstance().setObserver(this);
        RigLeConnectionManager.getInstance().connectDevice(device, 10000);
    }

    /**
     * Called if discovery times out before the bootloader device is found.
     */
    @Override
    public void discoveryDidTimeout() {
        RigLog.d("__RigFirmwareUpdateManager.discoveryDidTimetout__");
        RigLog.e("Did not find DFU device!!");
        handleUpdateError(RigDfuError.errorFromCode(RigDfuError.DISCOVERY_TIMEOUT));
    }

    /**
     * Called if bluetooth becomes disabled during a firmware update.
     * @param enabled The enabled or disabled state of Bluetooth on the Android device.
     */
    @Override
    public void bluetoothPowerStateChanged(boolean enabled) {
        RigLog.w("__RigFirmwareUpdateManager.bluetoothPowerStateChanged__");
    }

    /**
     * Call if bluetooth is not supported on this device.
     */
    @Override
    public void bluetoothDoesNotSupported() {
        RigLog.e("__RigFirmwareUpdateManager.bluetoothLeNotSupported__");
    }

    /* IRigLeConnectionManagerObserver methods */

    /**
     * Called after a successful connection has been made to the bootloader device.
     *
     * We check for {@link FirmwareManagerStateEnum#State_Cancelled} here and trigger a reset if
     * the update has been cancelled. See {@link #cancelUpdate()} for more information.
     *
     * @param device The newly connected device
     */
    @Override
    public void didConnectDevice(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateManager.didConnectDevice__");
        mUpdateDevice = device;

        if(mState == FirmwareManagerStateEnum.State_Cancelled) {
            cancelUpdate();
            return;
        }

        RigLeConnectionManager.getInstance().setObserver(mOldConnectionObserver);
        mFirmwareUpdateService.setShouldReconnectState(true);
        mFirmwareUpdateService.setDevice(device);
        mFirmwareUpdateService.triggerServiceDiscovery();
    }

    /**
     * Called if a device disconnects. This can be the original device
     * or Dfu.
     *
     * @param btDevice The disconnected Bluetooth Device object
     */
    @Override
    public void didDisconnectDevice(BluetoothDevice btDevice) {
        RigLog.i("__RigFirmwareUpdateManager.didDisconnectDevice__");

        //The firmware update was cancelled and the bootloader reset
        if(mState == FirmwareManagerStateEnum.State_Cancelled) {
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.FIRMWARE_UPDATE_CANCELLED));
            return;
        }

        //The device disconnected and entered the bootloader. Continue the update process.
        if(btDevice.getAddress().equals(mInitialDeviceAddress)) {
            //Give this to the original observer
            mFirmwareUpdateService.didDisconnectInitialNonBootloaderDevice();
            return;
        }

        //RigDfu disconnected before the firmware update completed
        if(mState.ordinal() < FirmwareManagerStateEnum.State_ImageValidationWriteCompletedAndPassed.ordinal()) {
            handleUpdateError(RigDfuError.errorFromCode(RigDfuError.BOOTLOADER_DISCONNECT));
        }
    }

    /**
     * Called if the connection to the bootloader device fails.
     *
     * @param device The available device data for the failed connection request
     */
    @Override
    public void deviceConnectionDidFail(RigAvailableDeviceData device) {
        RigLog.e("__RigFirmwareUpdateManager.deviceConnectionDidFail__");
        handleUpdateError(RigDfuError.errorFromCode(RigDfuError.CONNECTION_FAILED));
    }

    /**
     * Called if the connection request to the bootloader device times out before a
     * successful connection is achieved.
     *
     * @param device The available device data for the connection request
     */
    @Override
    public void deviceConnectionDidTimeout(RigAvailableDeviceData device) {
        RigLog.e("__RigFirmwareUpdateManager.deviceConnectionDidTimeout__");
        handleUpdateError(RigDfuError.errorFromCode(RigDfuError.CONNECTION_TIMEOUT));
    }
}
