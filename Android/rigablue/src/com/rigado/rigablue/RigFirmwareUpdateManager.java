package com.rigado.rigablue;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.UUID;

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
    DfuOpCode_EraseSizeRequest
}

/**
 * Enum for tracking the current state of the firmware update.
 */
enum FirmwareManagerStateEnum {
    State_Init,
    State_UserUnpluggedDeviceAwaitingReconnect,
    State_DiscoverFirmwareServiceCharacteristics,
    State_CheckEraseAfterUnplug,
    State_TriggeredErase,
    State_ReconnectAfterInitialFlashErase,
    State_TransferringStmUpdateImage,
    State_ActivatingStmUpdaterImage,
    State_ReconnectAfterStmUpdate,
    State_WaitReconnectAfterStmUpdateAndInvalidate,
    State_ReconnectAfterStmUpdateFlashErase,
    State_TransferringRadioImage,
    State_FinishedRadioImageTransfer,
    State_Cancelled
}

/**
 * This class manages the entire firmware update process based on the provided input images and
 * bootloader reset command.
 */
public class RigFirmwareUpdateManager implements IRigLeDiscoveryManagerObserver, IRigLeConnectionManagerObserver,
                                                IRigFirmwareUpdateServiceObserver {
    private static final int PacketReceivedNotificationRequest = 8;
    private static final int NumberOfPackets = 1;
    private static final int PacketReceivedNotification = 17;
    private static final int ReceivedOpcode = 16;
    private static final int OperationSuccess = 1;

    private static final int BytesInOnePacket = 20;
    
    private static final int ImageStartIndex = 0;
    private static final int ImageStartPacketSize = 12;
    private static final int ImageInitPacketIndex = ImageStartPacketSize;
    private static final int ImageInitPacketSize = 32;
    private static final int ImageSecureDataStart = ImageInitPacketIndex + ImageInitPacketSize;

    private RigFirmwareUpdateService mFirmwareUpdateService;
    private FirmwareManagerStateEnum mState;

    public static final int DiscoveryTimeoutError = -30;
    public static final int ConnectionFailedError = -31;
    public static final int ConnectionTimeoutError = -32;


    /**
     * Array to hold the firmware image being sent to the bootloader
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
    public boolean updateFirmware(RigLeBaseDevice device, InputStream firmwareImage,
                                  BluetoothGattCharacteristic activateCharacteristic,
                                  byte[] activateCommand) {
        RigLog.i("__RigFirmwareUpdateManager.updateFirmware__");
        mUpdateDevice = device;
        try {
            mImageSize = firmwareImage.available();
            mImage = new byte[mImageSize];
            firmwareImage.read(mImage);
        } catch(IOException e) {
            RigLog.e("IOException occurred while reading binary image data!");
        }

        mFirmwareUpdateService = new RigFirmwareUpdateService();
        mFirmwareUpdateService.setObserver(this);

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

    public void cancelUpdate() {
        mState = FirmwareManagerStateEnum.State_Cancelled;

        mFirmwareUpdateService.setShouldAlwaysReconnectState(false);
        mFirmwareUpdateService.setShouldReconnectState(false);

        byte [] data = { (byte)DfuOpCodeEnum.DfuOpCode_SystemReset.ordinal() };
        mFirmwareUpdateService.writeDataToControlPoint(data);
        mDidSendCancel = true;
    }

    /**
     * Initializes the state of the firmware update manager object.
     */
    private void initStateVariables() {
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
     * Sends the command to put the device in to bootloader mode.
     *
     * @param characteristic The characteristic which accepts the bootloader activate command
     * @param command The command which causes the device to enter the bootloader
     */
    private void sendEnterBootloaderCommand(BluetoothGattCharacteristic characteristic, byte [] command) {
        RigLog.d("__RigFirmwareUpdateManager.sendEnterBootloaderCommand__");

        RigLeDiscoveryManager dm = RigLeDiscoveryManager.getInstance();
        dm.stopDiscoveringDevices();
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String dfuServiceUuidString = mFirmwareUpdateService.getDfuServiceUuidString();
        RigDeviceRequest dr = new RigDeviceRequest(new String [] { dfuServiceUuidString }, 0);
        dr.setObserver(this);
        dm.startDiscoverDevices(dr);
        if(mObserver != null) {
            mObserver.updateStatus("Searching for updater service...", 0);
        }

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
    	int size = mImageSize;
    	if(mFirmwareUpdateService.isSecureDfu()) {
    		size -= (ImageStartPacketSize + ImageInitPacketSize);
    	}
    	return size;
    }

    /**
     * Resets all state flags
     */
    private void resetFlags() {
        RigLog.d("__RigFirmwareUpdateManager.resetFlags()__");
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

        if(mFirmwareUpdateService.isSecureDfu()) {
        	byte [] data = new byte[ImageStartPacketSize];
        	System.arraycopy(mImage, 0, data, 0, ImageStartPacketSize);
        	
        	mFirmwareUpdateService.writeDataToPacketCharacteristic(data);
        	mObserver.updateStatus("Writing device update size", 0);
        	
        } else {
	        byte [] data = new byte[4];
	        data[0] = (byte)(mImageSize & 0xFF);
	        data[1] = (byte)((mImageSize >> 8) & 0xFF);
	        data[2] = (byte)((mImageSize >> 16) & 0xFF);
	        data[3] = (byte)((mImageSize >> 24) & 0xFF);
	
	        RigLog.d("File size array: " + Arrays.toString(data));

        	mFirmwareUpdateService.writeDataToPacketCharacteristic(data);
        	mObserver.updateStatus("Writing device update size", 0);
        }
    }

    /**
     * Helper function to enable packet notifications
     */
    private void enablePacketNotification() {
        RigLog.d("__enablePacketNotification__");
        byte [] data = { PacketReceivedNotificationRequest, NumberOfPackets, 0 };

        mFirmwareUpdateService.writeDataToControlPoint(data);
        mObserver.updateStatus("Enabling packet notifications", 0);
    }

    /**
     * Sends the init packet to the secure bootloader.  The init packet contains information
     * regarding the encryption of the firmware image and which application binaires are being
     * sent.  Typically, only the application binary is sent.
     */
    private void sendInitPacket() {
    	RigLog.d("__sendInitPacket__");
    	byte [] initPacketOne = new byte[ImageInitPacketSize/2];
    	byte [] initPacketTwo = new byte[ImageInitPacketSize/2];
    	
    	System.arraycopy(mImage, ImageInitPacketIndex, initPacketOne, 0, ImageInitPacketSize/2);
    	System.arraycopy(mImage, ImageInitPacketIndex + (ImageInitPacketSize/2), initPacketTwo, 0, ImageInitPacketSize/2);
    	
    	mFirmwareUpdateService.writeDataToPacketCharacteristic(initPacketOne);
    	
    	try {
    		Thread.sleep(100);
    	} catch (Exception e) {
    		
    	}
    	mFirmwareUpdateService.writeDataToPacketCharacteristic(initPacketTwo);
    }

    /**
     * Commands the bootloader to prepare for receiving the firmware image.
     */
    private void receiveFirmwareImage() {
        RigLog.d("__receiveFirmwareImage__");
        byte [] data = { (byte)DfuOpCodeEnum.DfuOpCode_ReceiveFirmwareImage.ordinal() };

        mFirmwareUpdateService.writeDataToControlPoint(data);
    }

    /**
     * Starts the transfer of the firmware image data.
     */
    private void startUploadingFile() {
        RigLog.d("__startUploadingFile__");
        int size = getImageSize();
        mTotalPackets = (size / BytesInOnePacket);
        if((size % BytesInOnePacket) != 0) {
            mTotalPackets++;
        }

        determineLastPacketSize();

        mObserver.updateStatus("Transferring New Device Software", 0);
        sendPacket();
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
    	RigLog.d("Last packet size: " + mLastPacketSize);
    }

    /**
     * Sends the next firmware image packet.
     */
    private void sendPacket() {
        RigLog.d("__RigFirmwareUpdateManager.sendPacket__");

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
        if(mFirmwareUpdateService.isSecureDfu()) {
        	index = ImageSecureDataStart + (mPacketNumber - 1) * BytesInOnePacket;
        } else {
        	index = (mPacketNumber - 1) * BytesInOnePacket;
        }

        System.arraycopy(mImage, index, packet, 0, packetSize);
        mFirmwareUpdateService.writeDataToPacketCharacteristic(packet);
    }

    /**
     * Instructs the bootloader to validate the firmware image that was sent.  This is called
     * after the last packet has been received by the bootloader.
     */
    private void validateFirmware() {
        RigLog.d("__RigFirmwareUpdateManager.validateFirmware__");
        byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_ValidateFirmwareImage.ordinal() };

        mFirmwareUpdateService.writeDataToControlPoint(cmd);
        mObserver.updateStatus("Validating updated device software", 0);
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

        mFirmwareUpdateService.writeDataToControlPoint(cmd);
        mObserver.updateStatus("Activating device software", 0);
    }

    private void handleUpdateError(int error) {

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
        	mObserver.updateProgress(0);
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
        mFirmwareUpdateService.writeDataToControlPoint(cmd);
        mObserver.updateStatus("Initializing Device Firmware Update", 0);
    }

    /**
     * Called anytime a characteristic has been successfully written.
     */
    @Override
    public void didWriteValueForControlPoint() {
        RigLog.d("__RigFirmwareUpdateManager.didWriteValueForControlPoint__");

        if(mState == FirmwareManagerStateEnum.State_Cancelled && mDidSendCancel) {
            cleanUp();
            return;
        }

        if(mShouldWaitForErasedSize) {
            return;
        }

        if(mState == FirmwareManagerStateEnum.State_FinishedRadioImageTransfer) {
        	try {
        		Thread.sleep(2000);
        	} catch(Exception e) {
        		
        	}
            mObserver.didFinishUpdate();
        }

        if(!mIsFileSizeWritten) {
            writeFileSize();
        } else if(!mIsInitPacketSent && mFirmwareUpdateService.isSecureDfu()) {
        	sendInitPacket();
        } else if(!mIsPacketNotificationEnabled) {
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
     */
    @Override
    public void didDisconnectPeripheral() {
        RigLog.d("__RigFirmwareUpdateManager.didDisconnectDevice__");
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
            if(value[2] == OperationSuccess) {
                RigLog.i("Received notification for DFU_START");
                mIsFileSizeWritten = true;
                if(mFirmwareUpdateService.isSecureDfu()) {
                	RigLog.d("Start Init packet sequence");
                	byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_Init.ordinal() };
                	mFirmwareUpdateService.writeDataToControlPoint(cmd);
                } else {
                	enablePacketNotification();
                }
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_Init.ordinal()) {
        	if(value[2] == OperationSuccess) {
        		mIsInitPacketSent = true;
        		enablePacketNotification();
        	} else {
                this.handleUpdateError(value[2]);
            }
        } else if(opCode == PacketReceivedNotification) {
            mTotalBytesSent = ((value[1] & 0xFF) + ((value[2] & 0xFF) << 8) + ((value[3] & 0xFF) << 16) + ((value[4] & 0xFF) << 24));

            float progress = (float)mTotalBytesSent / (float)mImageSize;
            int progressPercentage = (int)(progress * 100);
            mObserver.updateProgress(progressPercentage);
            RigLog.i("Transferred " + mTotalBytesSent + "/" + mImageSize);

            if(!mIsLastPacket && !mShouldStopSendingPackets) {
                if((mTotalBytesSent / 20) != mPacketNumber) {
                    RigLog.e("Data consistency error!!!!");
                }
                sendPacket();
            } else {
                RigLog.i("Last packet notification received");
                mObserver.updateProgress(100);
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_ReceiveFirmwareImage.ordinal()) {
            if(value[2] == OperationSuccess) {
                RigLog.i("Firmware transfer successful");
                mObserver.updateStatus("Firmware transfer successful.  Validating...", 0);
                validateFirmware();
            } else {
                RigLog.e("Error during firmware image transfer " + value[2]);
                mObserver.updateStatus("Error during firmware transfer", value[2]);
                this.handleUpdateError(value[2]);
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_ValidateFirmwareImage.ordinal()) {
            if(value[2] == OperationSuccess) {
                RigLog.i("Successful transfer and validation of firmware image!");
                if(mState == FirmwareManagerStateEnum.State_TransferringRadioImage) {
                    mObserver.updateStatus("Device software validated successfully!", 0);
                    mState = FirmwareManagerStateEnum.State_FinishedRadioImageTransfer;
                }
                activateFirmware();
            } else {
                //TODO: Figure out how to recover from this situation
                RigLog.e("An error occurred during firmware image validation " + value[2]);
                mObserver.updateStatus("Error during validation!", value[2]);
            }
        } else if(opCode == ReceivedOpcode && request == (byte)DfuOpCodeEnum.DfuOpCode_EraseSizeRequest.ordinal()) {
            if(value[2] == OperationSuccess) {
            	/* Note: This is not longer used for the dual bank bootloader.  The entire bank does not need to be
            	 * erased to perform a firmware update.  In additional, as of S110 7.0, it is no longer necessary to
            	 * disable the radio when reading/write to the internal flash of the nRF.  This code is left for reference
            	 * and backwards compatibility with old bootloaders.
            	 */
                mTotalBytesErased = (value[3] + (value[4] << 8) + (value[5] << 16) + (value[6] << 24)) & 0xFFFFFFFF;
                RigLog.i("TotalBytesErased: " + mTotalBytesErased);
                if(mTotalBytesErased < mImageSize) {
                    byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_EraseAndReset.ordinal() };
                    if(mState == FirmwareManagerStateEnum.State_CheckEraseAfterUnplug) {
                        mState = FirmwareManagerStateEnum.State_ReconnectAfterInitialFlashErase;
                    } else if(mState == FirmwareManagerStateEnum.State_ReconnectAfterStmUpdate) {
                        mState = FirmwareManagerStateEnum.State_ReconnectAfterStmUpdateFlashErase;
                    }
                    mFirmwareUpdateService.setShouldReconnectState(true);
                    mFirmwareUpdateService.writeDataToControlPoint(cmd);
                } else {
                    byte [] cmd = { (byte)DfuOpCodeEnum.DfuOpCode_Start.ordinal() };
                    mShouldWaitForErasedSize = false;
                    if(mState == FirmwareManagerStateEnum.State_CheckEraseAfterUnplug) {
                        mState = FirmwareManagerStateEnum.State_TransferringStmUpdateImage;
                    }
                    mFirmwareUpdateService.writeDataToControlPoint(cmd);
                }
            }
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
        RigLog.d("Found dfu device!");
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
        handleUpdateError(DiscoveryTimeoutError);
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
     * @param device The newly connected device
     */
    @Override
    public void didConnectDevice(RigLeBaseDevice device) {
        RigLog.d("__RigFirmwareUpdateManager.didConnectDevice__");
        RigLog.d("Connected!");
        mUpdateDevice = device;

        RigLeConnectionManager.getInstance().setObserver(mOldConnectionObserver);
        mFirmwareUpdateService.setShouldReconnectState(true);
        mFirmwareUpdateService.setDevice(device);
        mFirmwareUpdateService.triggerServiceDiscovery();
    }

    /**
     * Called if the bootloader device disconnects
     *
     * @param btDevice The disconnected Bluetooth Device object
     */
    @Override
    public void didDisconnectDevice(BluetoothDevice btDevice) {
        if(btDevice.getAddress().equals(mInitialDeviceAddress)) {
            //Give this to the original observer
            mFirmwareUpdateService.didDisconnectInitialNonBootloaderDevice();
        }
    }

    /**
     * Called if the connection to the bootloader device fails.
     *
     * @param device The available device data for the failed connection request
     */
    @Override
    public void deviceConnectionDidFail(RigAvailableDeviceData device) {
        RigLog.e("RigFirmwareUpdateManager.deviceConnectionDidFail:Connection failed!");
        handleUpdateError(ConnectionFailedError);
    }

    /**
     * Called if the connection request to the bootloader device times out before a
     * successful connection is achieved.
     *
     * @param device The available device data for the connection request
     */
    @Override
    public void deviceConnectionDidTimeout(RigAvailableDeviceData device) {
        RigLog.e("RigFirmwareUpdateManager.deviceConnectionDidTimeout:Connection failed!");
        //Try again??
        //RigLeConnectionManager.getInstance().connectDevice(device, 10000);
        //EPS - This change was made to support a client project.  It may not be the correct way to handle this event.
        handleUpdateError(ConnectionTimeoutError);
    }
}
