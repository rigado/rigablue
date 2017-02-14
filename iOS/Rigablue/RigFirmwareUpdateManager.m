//
//  RigFirmwareUpdateManager.m
//  Rigablue Library
//
//  Created by Eric Stutzenberger on 11/8/13.
//  Copyright © 2017 Rigado, Inc. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for a copy.

#import "RigFirmwareUpdateManager.h"
#import "RigFirmwareUpdateService.h"
#import "RigDeviceRequest.h"
#import "RigLeDiscoveryManager.h"
#import "RigAvailableDeviceData.h"
#import "RigLeConnectionManager.h"

#define DFU_SEARCH_TIMEOUT                      60.0   /* seconds */
#define IMAGE_START_PACKET_IDX                  0
#define IMAGE_START_PACKET_SIZE                 12
#define IMAGE_INIT_PACKET_IDX                   IMAGE_START_PACKET_SIZE
#define IMAGE_INIT_PACKET_SIZE                  32
#define PATCH_INIT_PACKET_IDX                   IMAGE_START_PACKET_SIZE + IMAGE_INIT_PACKET_SIZE
#define PATCH_INIT_PACKET_SIZE                  12
#define IMAGE_SECURE_DATA_START                 IMAGE_INIT_PACKET_IDX + IMAGE_INIT_PACKET_SIZE

/* Operation Codes for controlling the DFU */
#define DFU_START                               1
#define INITIALIZE_DFU                          2  /* Available but not used */
#define RECEIVE_FIRMWARE_IMAGE                  3
#define VALIDATE_FIRMWARE_IMAGE                 4
#define ACTIVATE_FIRMWARE_AND_RESET             5
#define SYSTEM_RESET                            6
#define INITIALIZE_PATCH                        10
#define RECEIVE_PATCH_IMAGE                     11

#define RESPONSE                                16
#define PACKET_RECEIVED_NOTIFICATION_REQUEST    8
#define NUMBER_OF_PACKETS                       1

#define PACKET_RECEIVED_NOTIFICATION            17
#define RECEIVED_OPCODE                         16

#define OPERATION_SUCCESS                       1
#define OPERATION_INVALID_STATE                 2
#define OPERATION_NOT_SUPPORTED                 3
#define OPERATION_DATA_SIZE_EXCEEDS_LIMIT       4
#define OPERATION_CRC_ERROR                     5
#define OPERATION_OPERATION_FAILED              6
#define OPERATION_PATCH_NEED_MORE_DATA          7
#define OPERATION_PATCH_INPUT_IS_FULL           8

/* Device Packet size */
#define BYTES_IN_ONE_PACKET                     20

NSUInteger kFirmwareKeyLength = 16;

typedef enum FirmwareManagerState_enum
{
    /* Initial state of update manager */
    State_Init,
    /* This state is used just before discovering services on the device */
    State_DiscoverFirmwareServiceCharacteristics,
    /* This state is used during image transfer */
    State_TransferringRadioImage,
    /* This is the final state once the transfer is complete */
    State_FinishedRadioImageTransfer,
    /* This is the state if the Firmware Update should cancel */
    State_FirmwareUpdateCanceled
} eFirmwareManagerState;

@interface RigFirmwareUpdateManager() <RigFirmwareUpdateServiceDelegate, RigLeDiscoveryManagerDelegate, RigLeBaseDeviceDelegate, RigLeConnectionManagerDelegate>
{
    
}
@end

@implementation RigFirmwareUpdateManager
{
    RigFirmwareUpdateService *firmwareUpdateService;
    eFirmwareManagerState state;
    
    NSData * image;
    uint32_t imageSize;
    
    BOOL isFileSizeWritten;
    BOOL isInitPacketSent;
    BOOL isPacketNotificationEnabled;
    BOOL isReceivingFirmwareImage;
    BOOL isLastPacket;
    BOOL shouldStopSendingPackets;
    BOOL isPatchUpdate;
    BOOL isPatchInitPacketSent;
    
    uint32_t totalPackets;
    uint32_t packetNumber;
    uint32_t totalBytesSent;
    uint8_t lastPacketSize;
    
    RigLeBaseDevice *bootloaderDevice;
    RigLeBaseDevice *baseDevice;
    id<RigLeBaseDeviceDelegate> oldBaseDeviceDelegate;
}

@synthesize delegate;

- (id)init
{
    self = [super init];
    if (self) {
        [self initStateVariables];
    }
    return self;
}

- (void)initStateVariables
{
    isFileSizeWritten = NO;
    isInitPacketSent = NO;
    
    isPacketNotificationEnabled = NO;
    isReceivingFirmwareImage = NO;
    isLastPacket = NO;
    shouldStopSendingPackets = NO;
    isPatchUpdate = NO;
    isPatchInitPacketSent = NO;
    
    delegate = nil;
    baseDevice = nil;
    oldBaseDeviceDelegate = nil;
    state = State_Init;
    imageSize = 0;
    image = nil;
}

- (BOOL)firmwareImageIsPatch:(NSData*)firmwareImage
{
    if (firmwareImage.length < kFirmwareKeyLength) {
        return NO;
    }
    
    // create data object with first 16 bytes of firmware image
    NSData *key = [firmwareImage subdataWithRange:NSMakeRange(0, kFirmwareKeyLength)];
    
    //convert patchKey to NSData and compare
    uint8_t patch_key[] = {
        0xac, 0xb3, 0x37, 0xe8, 0xd0, 0xeb, 0x40, 0x90,
        0xa4, 0xf3, 0xbb, 0x85, 0x7a, 0x5b, 0x2a, 0xf6
    };
    NSData *keyPatchData = [NSData dataWithBytes:patch_key length:kFirmwareKeyLength];
    
    return [keyPatchData isEqualToData:key];
}

- (RigDfuError_t)updateFirmware:(RigLeBaseDevice*)device image:(NSData*)firmwareImage activateChar:(CBCharacteristic*)characteristic
                activateCommand:(uint8_t*)command activateCommandLen:(uint8_t)commandLen
{
    RigDfuError_t result = DfuError_None;
    NSLog(@"__updateFirmware__");
    
    isPatchUpdate = NO;
    imageSize = (UInt32)firmwareImage.length;
    image = firmwareImage;
    
    if ([self firmwareImageIsPatch:firmwareImage]) {
        isPatchUpdate = YES;
        imageSize = imageSize - (UInt32)kFirmwareKeyLength;
        image = [firmwareImage subdataWithRange:NSMakeRange(kFirmwareKeyLength, imageSize)];
    }
    
    // We hold on to an instance of the device and its delegate because the delegate will be overridden by firmwareUpdateService
    // and we will reset it in cleanUpAfterFailure
    oldBaseDeviceDelegate = device.delegate;
    baseDevice = device;
    
    // Create the firmware update service object and assigned this object as the delegate
    firmwareUpdateService = [[RigFirmwareUpdateService alloc] init];
    firmwareUpdateService.delegate = self;
    result = [firmwareUpdateService setDevice:device];
    if (result != DfuError_None) {
        return result;
    }
    
    //Set to automatically reconnect.  This will force iOS to connect again immediately after receving an advertisement packet from the peripheral after
    //activating the bootloader.
    firmwareUpdateService.shouldReconnectToPeripheral = YES;
    state = State_DiscoverFirmwareServiceCharacteristics;
    
    //If already connected to a DFU, then start the update, otherwise send Bootloader activation command
    
    CBService *dfuService;
    if (firmwareUpdateService.updateDFUServiceUuidString) {
        dfuService = [device getServiceWithUuid:[CBUUID UUIDWithString:firmwareUpdateService.updateDFUServiceUuidString]];
    }
    if (dfuService != nil) {
        if (device.peripheral.state == CBPeripheralStateConnected) {
            if (!device.isDiscoveryComplete) {
                result = [firmwareUpdateService triggerServiceDiscovery];
                if (result != DfuError_None) {
                    return result;
                }
            } else {
                [firmwareUpdateService determineSecureDfuStatus];
                result = [firmwareUpdateService enableControlPointNotifications];
                if (result != DfuError_None) {
                    return result;
                }
            }
        } else {
            result = [firmwareUpdateService connectPeripheral];
            if (result != DfuError_None) {
                return result;
            }
        }
    } else {
        if (characteristic == nil || device == nil || device.peripheral == nil || command == nil) {
            NSLog(@"Invalid parameter provided!");
            return DfuError_InvalidParameter;
        }
        
        if (characteristic.properties & CBCharacteristicPropertyWriteWithoutResponse) {
            [device.peripheral writeValue:[NSData dataWithBytes:command length:commandLen] forCharacteristic:characteristic type:CBCharacteristicWriteWithoutResponse];
        } else if (characteristic.properties & CBCharacteristicPropertyWrite) {
            [device.peripheral writeValue:[NSData dataWithBytes:command length:commandLen] forCharacteristic:characteristic type:CBCharacteristicWriteWithResponse];
        } else {
            NSLog(@"Update characteristic is not writeable");
            return DfuError_InvalidParameter;
        }
        
        RigLeDiscoveryManager *dm = [RigLeDiscoveryManager sharedInstance];
        
        firmwareUpdateService.shouldReconnectToPeripheral = NO;
        CBUUID *dfuServiceUuid200 = [CBUUID UUIDWithString:kupdateDFUServiceUuidString200];
        CBUUID *dfuServiceUuid300 = [CBUUID UUIDWithString:kupdateDFUServiceUuidString300];
        NSArray *uuidList = @[dfuServiceUuid200, dfuServiceUuid300];
        RigDeviceRequest *dr = [RigDeviceRequest deviceRequestWithUuidList:uuidList timeout:DFU_SEARCH_TIMEOUT delegate:self allowDuplicates:YES];
        [dm discoverDevices:dr];
        [delegate updateStatus:@"Searching for Update Service..." errorCode:DfuError_None];
    }
    
    return result;
}

- (RigDfuError_t)performUpdate:(RigFirmwareUpdateRequest*)request
{
    return [self updateFirmware:request.updateDevice
                          image:request.image
                   activateChar:request.activationCharacteristic
                activateCommand:(uint8_t *)[request.activationCommand bytes]
             activateCommandLen:request.activationCommand.length];
    
}

- (void)cancelFirmwareUpdate
{
    state = State_FirmwareUpdateCanceled;
    RigDfuError_t result = [self sendCancelCommand];
    if (result != DfuError_ControlPointCharacteristicMissing && result != DfuError_None) {
        if ([delegate respondsToSelector:@selector(cancelFailedWithErrorCode:)]) {
            [delegate cancelFailedWithErrorCode:result];
            
        }
        NSLog(@"Error occured with Firmware Update Cancel");
    }
    
    // If cancelFirmwareUpdate is called before the bootloader has connected,
    // We will get an error: DfuError_ControlPointCharacteristicMissing
    
    // When the firmwareUpdateManager calls enableControlPointNotification
    // it will see we are in a "canceled" state, and send the reset command again.
}

- (RigDfuError_t)sendCancelCommand {
    uint8_t resetCommand[] = { SYSTEM_RESET };
    // We write with response, but take action immediately since the device will reset before responding
    RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:resetCommand withLen:1 shouldGetResponse:YES];
    
    if (result == DfuError_None) {
        if ([delegate respondsToSelector:@selector(updateCanceled)]) {
            [delegate updateCanceled];
        }
        [self cleanUpAfterFailure];
    }
    return result;
}


- (void)cleanUpAfterFailure
{
    /* Reassign connection and baseDevice delegate */
    [firmwareUpdateService completeUpdate];
    baseDevice.delegate = oldBaseDeviceDelegate;
    
    /* For device disconnection if connected */
    if (bootloaderDevice != nil) {
        if (bootloaderDevice.peripheral.state == CBPeripheralStateConnected ||
            bootloaderDevice.peripheral.state == CBPeripheralStateConnecting) {
            [[RigLeConnectionManager sharedInstance] disconnectDevice:bootloaderDevice];
        }
    }
    
    [self initStateVariables];
    firmwareUpdateService.alwaysReconnectOnDisconnect = NO;
    firmwareUpdateService.shouldReconnectToPeripheral = NO;
}

- (uint32_t)getImageSize
{
    uint32_t size = imageSize;
    if ([firmwareUpdateService isSecureDfu]) {
        size -= (IMAGE_START_PACKET_SIZE + IMAGE_INIT_PACKET_SIZE);
        if(isPatchUpdate) {
            size -= PATCH_INIT_PACKET_SIZE;
        }
    }
    
    return size;
}

- (uint32_t)getImageStart
{
    if (isPatchUpdate) {
        return IMAGE_SECURE_DATA_START + PATCH_INIT_PACKET_SIZE;
    } else {
        return IMAGE_SECURE_DATA_START;
    }
}

- (void)updateDeviceAndTriggerDiscovery
{
    RigDfuError_t result = DfuError_None;
    firmwareUpdateService.shouldReconnectToPeripheral = YES;
    result = [firmwareUpdateService setDevice:bootloaderDevice];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to set bootloader device."];
        [self cleanUpAfterFailure];
    }
    
    result = [firmwareUpdateService triggerServiceDiscovery];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to discover services for bootloader device or discovered services were invalid." ];
        [self cleanUpAfterFailure];
    }
}

/**
 *  Simply sends the size of the firmware image to the DFU.  This is expect after enabling packet notifications through a command to the control point.
 */
- (RigDfuError_t)writeFileSize
{
    NSLog(@"__writeFileSize__");
    RigDfuError_t result = DfuError_None;
    
    if ([firmwareUpdateService isSecureDfu]) {
        /* For the secure update, the start packet is stored within the first 12 bytes of the signed binary */
        uint8_t data[IMAGE_START_PACKET_SIZE];
        memcpy(data, image.bytes, sizeof(data));
        //TODO: Check if this is actually necessary
        /* EPS - Noticed that breakpoing here kept update from failing.  Invesitgate further later!! */
        //[NSThread sleepForTimeInterval:1.0f];
        
        result = [firmwareUpdateService writeDataToPacketCharacteristic:data withLen:sizeof(data) shouldGetResponse:NO];
        if (result != DfuError_None) {
            [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write image size to packet characteristic."];
            //TODO: Clean up after failure
            return result;
        }
        [delegate updateStatus:@"Writing Device Update Size and Type" errorCode:0];
    }
    else
    {
        uint8_t data[4];
        data[0] = imageSize & 0xFF;
        data[1] = (imageSize >> 8) & 0xFF;
        data[2] = (imageSize >> 16) & 0xFF;
        data[3] = (imageSize >> 24) & 0xFF;
        
        //TODO: Check if this is actually necessary
        /* EPS - Noticed that breakpoing here kept update from failing.  Invesitgate further later!! */
        [NSThread sleepForTimeInterval:1.0f];
        
        result = [firmwareUpdateService writeDataToPacketCharacteristic:data withLen:sizeof(data) shouldGetResponse:NO];
        if (result != DfuError_None) {
            [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write image size to packet characteristic."];
            //TODO: Clean up after failure
            return result;
        }
        [delegate updateStatus:@"Writing Device Update Size" errorCode:0];
    }
    
    return DfuError_None;
}

/**
 *  Enables notifications to be sent back as packets are received.  Note that this is a notification sent from the DFU on the control point
 *  and not an enablement of notifications on the Packet characteristic.
 */
- (RigDfuError_t)enablePacketNotifications
{
    RigDfuError_t result = DfuError_None;
    
    NSLog(@"__enablePacketNotifications__");
    uint8_t data[] = { PACKET_RECEIVED_NOTIFICATION_REQUEST, NUMBER_OF_PACKETS, 0 };
    
    result = [firmwareUpdateService writeDataToControlPoint:data withLen:sizeof(data) shouldGetResponse:YES];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write enable notifications for packets." ];
        //TODO: Clean up after failure
        return result;
    }
    [delegate updateStatus:@"Enabling Notifications" errorCode:0];
    return DfuError_None;
}


- (RigDfuError_t)sendInitPacket
{
    NSLog(@"__sendInitPacket__");
    RigDfuError_t result = DfuError_None;
    uint8_t initPacket[IMAGE_INIT_PACKET_SIZE];
    memcpy(initPacket, &image.bytes[IMAGE_INIT_PACKET_IDX], IMAGE_INIT_PACKET_SIZE);
    
    result = [firmwareUpdateService writeDataToPacketCharacteristic:initPacket withLen:IMAGE_INIT_PACKET_SIZE/2 shouldGetResponse:NO];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write init packet."];
        return result;
    }
    
    /* Note that the packet characteristic does not have write with repsonse capabilities, so these two writes are performed using a delay.  iOS
     * will not issue a callback when the characteristic has been written. */
    [NSThread sleepForTimeInterval:0.100f];
    
    result = [firmwareUpdateService writeDataToPacketCharacteristic:&initPacket[IMAGE_INIT_PACKET_SIZE/2] withLen:IMAGE_INIT_PACKET_SIZE/2 shouldGetResponse:NO];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write init packet."];
        return result;
    }
    return result;
}

- (RigDfuError_t)sendPatchInitPacket
{
    NSLog(@"__sendPatchInitPacket__");
    RigDfuError_t result = DfuError_None;
    uint8_t patchInitPacket[PATCH_INIT_PACKET_SIZE];
    memcpy(patchInitPacket, &image.bytes[PATCH_INIT_PACKET_IDX], PATCH_INIT_PACKET_SIZE);
    
    result = [firmwareUpdateService writeDataToPacketCharacteristic:patchInitPacket withLen:PATCH_INIT_PACKET_SIZE shouldGetResponse:NO];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write patch init packet."];
        return result;
    }
    
    return result;
}

/**
 *  Send this command prepares the DFU to receive the firmware image.
 */
- (RigDfuError_t)receiveFirmwareImage
{
    NSLog(@"__receiveFirmwareImage__");
    uint8_t data = RECEIVE_FIRMWARE_IMAGE;
    RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&data withLen:sizeof(data) shouldGetResponse:YES];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to initialize firmware image transfer."];
        return result;
    }
    return DfuError_None;
}

/**
 *  Initiates send firmware data to the DFU.
 */
- (RigDfuError_t)startUploadingFile
{
    NSLog(@"__startUploadingFile__");
    uint32_t size = [self getImageSize];
    totalPackets = (size / BYTES_IN_ONE_PACKET);
    if (size % BYTES_IN_ONE_PACKET > 0) {
        totalPackets++;
    }
    
    [self deterimeLastPacketSize];
    
    [delegate updateStatus:@"Transferring New Device Software" errorCode:0];
    RigDfuError_t result = [self sendPacket];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to start transfer of image data."];
        return result;
    }
    
    return DfuError_None;
}

/**
 *  Determines the size of the last packet that will be sent.  The firmware image is transferred in equal size packets,
 *  usually 20 bytes in size to minimize transfer time, except for the last packet if necessary.
 */
- (void)deterimeLastPacketSize
{
    uint32_t imageSizeLocal = [self getImageSize];
    
    if ((imageSizeLocal % BYTES_IN_ONE_PACKET) == 0) {
        lastPacketSize = BYTES_IN_ONE_PACKET;
    } else {
        lastPacketSize = (imageSizeLocal - ((totalPackets - 1) * BYTES_IN_ONE_PACKET));
    }
    
    NSLog(@"Last Packet Size: %d", lastPacketSize);
}

/**
 *  Sends one packet of data to the DFU.  If the packet being sent is the last packet, then the size
 *  is based on the size calculated in the call to determineLastPacketSize.
 */
- (RigDfuError_t)sendPacket
{
    packetNumber++;
    uint8_t packetSize = BYTES_IN_ONE_PACKET;
    
    /* Handle last packet */
    if (packetNumber == totalPackets) {
        NSLog(@"Sending last packet: %d", packetNumber);
        isLastPacket = YES;
        /* Adjust size for last packet */
        packetSize = lastPacketSize;
    } else {
        NSLog(@"Sending packet: %d/%d Bytes Sent: %d/%d", packetNumber, totalPackets, packetNumber * 20, [self getImageSize]);
    }
    
    NSRange range;
    uint32_t imageStart = [self getImageStart];
    if ([firmwareUpdateService isSecureDfu]) {
        range = NSMakeRange(imageStart + (packetNumber - 1) * BYTES_IN_ONE_PACKET, packetSize);
    } else {
        range = NSMakeRange((packetNumber - 1) * BYTES_IN_ONE_PACKET, packetSize);
    }
    
    NSData *dataToSend = [image subdataWithRange:range];
    RigDfuError_t result = [firmwareUpdateService writeDataToPacketCharacteristic:dataToSend.bytes withLen:dataToSend.length shouldGetResponse:NO];
    return result;
}

/**
 *  Sends the command to cause the DFU to validate the firmware.
 */
- (RigDfuError_t)validateFirmware
{
    NSLog(@"__validateFirmware__");
    uint8_t cmd = VALIDATE_FIRMWARE_IMAGE;
    RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to start firmware validation."];
        return result;
    }
    [delegate updateStatus:@"Validating Transferred Device Software" errorCode:DfuError_None];
    return DfuError_None;
}

/**
 *  Sends the command to activate the newly uploaded firmware.  This will cause the device to exit the DFU and start the application.
 */
- (RigDfuError_t)activateFirmware
{
    NSLog(@"__activateFirmware__");
    uint8_t cmd = ACTIVATE_FIRMWARE_AND_RESET;
    
    /* Reassign delegate prior to activation since a disconnect occurs after activation */
    firmwareUpdateService.shouldReconnectToPeripheral = NO;
    firmwareUpdateService.alwaysReconnectOnDisconnect = NO;
    [firmwareUpdateService completeUpdate];
    
    RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write packet characteristic."];
        return result;
    }
    
    [delegate updateStatus:@"Activating Updated Device Software" errorCode:DfuError_None];
    return DfuError_None;
}

#pragma mark - LeFirmwareUpdateServiceDelegate Methods

/**
 *  This method is sent from the delegate protocol once device service and characteristic discovery is complete.
 */
- (void)didDiscoverCharacteristicsForDFUService
{
    if (state == State_TransferringRadioImage) {
        state = State_Init;
        isFileSizeWritten = NO;
        isPacketNotificationEnabled = NO;
        isReceivingFirmwareImage = NO;
        isInitPacketSent = NO;
        isPatchInitPacketSent = NO;
        [delegate updateProgress:0.0f];
        packetNumber = 0;
    }
    RigDfuError_t result = [firmwareUpdateService enableControlPointNotifications];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to set notifications on control point."];
        [self cleanUpAfterFailure];
    }
}

/**
 *  This is called after successfully enabling notifications for the control point
 */
- (void)didEnableControlPointNotifications
{
    
    if (state == State_FirmwareUpdateCanceled) {
        RigDfuError_t result = [self sendCancelCommand];
        if (result != DfuError_None) {
            if ([delegate respondsToSelector:@selector(cancelFailedWithErrorCode:)]) {
                [delegate cancelFailedWithErrorCode:result];
            }
            NSLog(@"Error occured with Firmware Update Cancel");
        }
        return;
    }
    
    NSLog(@"__didEnableControlPointNotifications__");
    // This is sent after enabling notifications on the control point
    uint8_t cmd = DFU_START;
    NSLog(@"Sending DFU_START opcode");
    state = State_TransferringRadioImage;
    RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    if (result != DfuError_None) {
        [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write start DFU command to control point."];
        //TODO: Clean up after failure
        //return result;
    }
    [delegate updateStatus:@"Initializing Device Firmware Update" errorCode:DfuError_None];
}

/**
 *  This is called after succussfully writing data to the control point with a WriteWithResponse attribute
 */
- (void) didWriteValueForControlPoint
{
    NSLog(@"__didWriteValueForControlPoint__");
    RigDfuError_t result = DfuError_None;
    
    if (state == State_FinishedRadioImageTransfer) {
        //If the firmware update is now complete, finalize the update and notify the app.
        [NSThread sleepForTimeInterval:2.0];
        [delegate didFinishUpdate];
    }
    
    //This functionality behaves in tandem with the commands being written to the control point
    //from didUpdateValueForControlPoint
    if (!isFileSizeWritten) {
        result = [self writeFileSize];
    } else if(!isInitPacketSent && [firmwareUpdateService isSecureDfu]) {
        result = [self sendInitPacket];
    } else if(!isPatchInitPacketSent && isPatchUpdate) {
        result = [self sendPatchInitPacket];
    } else if(!isPacketNotificationEnabled && !isPatchUpdate) {
        isPacketNotificationEnabled = YES;
        result = [self receiveFirmwareImage];
    } else if(!isReceivingFirmwareImage) {
        isReceivingFirmwareImage = YES;
        result = [self startUploadingFile];
    }
    
    if (result != DfuError_None) {
        [self cleanUpAfterFailure];
    }
}

/**
 *  Called when a notification arrives from the DFU
 *
 *  @param value The data sent by the DFU
 */
- (void) didUpdateValueForControlPoint:(uint8_t *)value
{
    NSLog(@"__didUpdateValueForControlPoint__");
    //the first two bytes are the opcode sent back and the command that was sent to the dfu
    uint8_t opCode = value[0];
    uint8_t request = value[1];
    
    //NSLog(@"OpCode: %02x, Request: %02x", opCode, request);
    
    if (opCode == RECEIVED_OPCODE && request == DFU_START) {
        NSLog(@"Received notification for DFU_START");
        if (value[2] == OPERATION_SUCCESS) {
            //This is received after sending the size of the firmware image to the packet characteristic
            //Here, we mark the fact that the firmware image
            //size has been sent and enable notifications for packets.  This will cause a didWriteValueToCharacteristic
            //call to be generated by CoreBluetooth which will then cause the firmware image transfer to begin.
            isFileSizeWritten = YES;
            if ([firmwareUpdateService isSecureDfu]) {
                NSLog(@"Start init packet sequence");
                uint8_t cmd = INITIALIZE_DFU;
                RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
                if (result != DfuError_None) {
                    [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write dfu initialization to control point."];
                    //TODO: Clean up after failure
                    //return result;
                }
            } else {
                if ([self enablePacketNotifications] != DfuError_None) {
                    [self cleanUpAfterFailure];
                }
            }
        }
    } else if(opCode == RECEIVED_OPCODE && request == INITIALIZE_DFU) {
        NSLog(@"Received Notification for INITIALIZE_DFU");
        if(value[2] == OPERATION_SUCCESS) {
            //This is received after sending the patch initialization data.  If the update is not a patch, then this
            //step is skipped.
            isInitPacketSent = YES;
            if(isPatchUpdate) {
                NSLog(@"Start patch init sequence");
                uint8_t cmd = INITIALIZE_PATCH;
                RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
                if (result != DfuError_None) {
                    [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write patch initialization start to control point."];
                    //TODO: Clean up after failure
                    //return result;
                }
            } else {
                if ([self enablePacketNotifications] != DfuError_None) {
                    [self cleanUpAfterFailure];
                }
            }
        }
    } else if(opCode == RECEIVED_OPCODE && request == INITIALIZE_PATCH) {
        if(value[2] == OPERATION_SUCCESS) {
            NSLog(@"Received Notification for INITIALIZE_PATCH");
            isPatchInitPacketSent = YES;
            //At this point, the patching process and the normal update process diverge.  In the normal case, the packet notifications would be
            //enabled.  Instead, the page image transfer is started.
            NSLog(@"Start patch image transfer");
            uint8_t cmd = RECEIVE_PATCH_IMAGE;
            RigDfuError_t result = [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
            if (result != DfuError_None) {
                [self firmwareUpdateFailedFromError:result withErrorMessage:@"Failed to write patch initialization start to control point."];
                [self cleanUpAfterFailure];
            }
        } else if(value[2] == OPERATION_CRC_ERROR) {
            NSLog(@"CRC on patch initialization!");
            [self firmwareUpdateFailedFromError:DfuError_PatchCurrentImageCrcFailure withErrorMessage:@"CRC of current image does not match required CRC!"];
            [self cleanUpAfterFailure];
        } else {
            NSLog(@"Unexpected patch initialization error!");
            [self firmwareUpdateFailedFromError:DfuError_Unknown withErrorMessage:@"Unexpected patch init error!"];
            [self cleanUpAfterFailure];
        }
    } else if (opCode == PACKET_RECEIVED_NOTIFICATION) {
        //This is sent every time a packet is successfully received by the DFU.  This provides the app a way of
        //knowing that each packet has been received and the total size that has been transferred thus far.
        totalBytesSent = (value[1] + (value[2] << 8) + (value[3] << 16) + (value[4] << 24)) & 0xFFFFFFFF;
        
        [delegate updateProgress:(float)((float)totalBytesSent / (float)[self getImageSize])];
        
        //If we haven't sent the last packet yet, then keep sending packets.  Once sent, we will notify the app
        //that the firmware image has been fully transferred.
        if (!isLastPacket && !shouldStopSendingPackets) {
            if([self sendPacket]) {
                [self cleanUpAfterFailure];
            }
        } else {
            NSLog(@"Last packet notification received");
            [delegate updateProgress:1.0];
        }
    } else if (opCode == RECEIVED_OPCODE && request == RECEIVE_FIRMWARE_IMAGE) {
        NSLog(@"Opcode: Receive Firmware Image");
        if (value[2] == OPERATION_SUCCESS) {
            //This is sent by the DFU after receiving the all data for the firmware image. At this point, the DFU
            //needs to validate the firmware image, so that command is sent to the device.
            NSLog(@"Firmware transfer successful");
            [delegate updateStatus:@"Successfully Transferred Software.  Validating..." errorCode:DfuError_None];
            RigDfuError_t result = [self validateFirmware];
            if(result != DfuError_None) {
                [self firmwareUpdateFailedFromError:result withErrorMessage:@"Could not initialize firmware validation!"];
                [self cleanUpAfterFailure];
            }
        } else {
            NSLog(@"Error on firmware transfer: %d", value[2]);
            [self firmwareUpdateFailedFromError:DfuError_ImageValidationFailure withErrorMessage:@"Firmware Validation Failed!"];
            [self cleanUpAfterFailure];
        }
    } else if(opCode == RECEIVED_OPCODE && request == RECEIVE_PATCH_IMAGE) {
        if(value[2] == OPERATION_PATCH_NEED_MORE_DATA) {
            [delegate updateProgress:((float)(packetNumber * 20) / (float)[self getImageSize])];
            //TODO: Report progress
            if(!shouldStopSendingPackets) {
                //update delegate
                [self sendPacket];
            }
        } else if(value[2] == OPERATION_SUCCESS) {
            [delegate updateProgress:1.0];
            NSLog(@"All patch data sent successfully");
            [delegate updateStatus:@"Successfully Transferred Software.  Validating..." errorCode:DfuError_None];
            RigDfuError_t result = [self validateFirmware];
            if(result != DfuError_None) {
                [self firmwareUpdateFailedFromError:result withErrorMessage:@"Could not initialize firmware validation!"];
                [self cleanUpAfterFailure];
            }
        } else {
            
        }
    }
    else if (opCode == RECEIVED_OPCODE && request == VALIDATE_FIRMWARE_IMAGE) {
        NSLog(@"Firmware validated");
        if (value[2] == OPERATION_SUCCESS) {
            NSLog(@"Successful verification and transfer of firmware!");
            
            //Once the firmware is validated, the firmware transfer is considered successful and the activation command
            //is sent to the DFU.
            if(state == State_TransferringRadioImage) {
                [delegate updateStatus:@"Device Software Validated Successfully!" errorCode:DfuError_None];
                state = State_FinishedRadioImageTransfer;
                RigDfuError_t result = [self activateFirmware];
                if(result != DfuError_None) {
                    [self firmwareUpdateFailedFromError:result withErrorMessage:@"Could not activate updated firmware!"];
                    [self cleanUpAfterFailure];
                }
            }
        } else {
            if (value[2] == OPERATION_CRC_ERROR) {
                NSLog(@"CRC Failure on Validation!");
                [self firmwareUpdateFailedFromError:DfuError_PostPatchImageCrcFailure withErrorMessage:@"Either image post patch CRC failed or the encrypted data was incorrect!"];
                [self cleanUpAfterFailure];
            } else {
                NSLog(@"Error occurred during firmware validation");
                [self firmwareUpdateFailedFromError:DfuError_ImageValidationFailure withErrorMessage:@"Firmware Validation Failed!"];
                [self cleanUpAfterFailure];
            }
        }
    }
}

- (void)firmwareUpdateFailedFromError:(RigDfuError_t)error withErrorMessage:(NSString *)errorMessage {
    if ([delegate respondsToSelector:@selector(updateFailed:errorCode:)]) {
        [delegate updateFailed:errorMessage errorCode:error];
    }
}

- (void)didFailToConnectToBootloader
{
    [self firmwareUpdateFailedFromError:DfuError_CouldNotConnect withErrorMessage:@"Could not connect to Bootloader."];
    [self cleanUpAfterFailure];
}

#pragma mark -
#pragma mark - RigLeDiscoveryManagerDelegate methods
- (void)didDiscoverDevice:(RigAvailableDeviceData *)device
{
    if ([device.peripheral.name isEqual:@"RigDfu"] && device.rssi.integerValue > -65 && device.rssi.integerValue < 0) {
        
        [[RigLeDiscoveryManager sharedInstance] stopDiscoveringDevices];
        [RigLeConnectionManager sharedInstance].delegate = self;
        [[RigLeConnectionManager sharedInstance] connectDevice:device connectionTimeout:10.0f];
        
    }
}

- (void)bluetoothNotPowered
{
    
}

- (void)discoveryDidTimeout
{
    NSLog(@"Did not find DFU Device!!");
    [self didFailToConnectToBootloader];
}

#pragma mark -
#pragma mark - RigLeConnectionManagerDelegrate methods
-(void)didConnectDevice:(RigLeBaseDevice *)device
{
    bootloaderDevice = device;
    [RigLeConnectionManager sharedInstance].delegate = firmwareUpdateService;
    [self performSelectorOnMainThread:@selector(updateDeviceAndTriggerDiscovery) withObject:nil waitUntilDone:NO];
}

-(void)didDisconnectPeripheral:(CBPeripheral *)peripheral
{
    NSLog(@"Connection failed!");
}

- (void)deviceConnectionDidFail:(RigAvailableDeviceData *)device
{
    [self didFailToConnectToBootloader];
}

- (void)deviceConnectionDidTimeout:(RigAvailableDeviceData *)device
{
    [self didFailToConnectToBootloader];
}

#pragma mark -
#pragma mark - RigLeBaseDeviceDelegate methods
- (void)discoveryDidCompleteForDevice:(RigLeBaseDevice *)device
{
    
}

- (void)didUpdateNotifyStateForCharacteristic:(CBCharacteristic *)characteristic forDevice:(RigLeBaseDevice *)device
{
    
}

- (void)didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic forDevice:(RigLeBaseDevice *)device
{
    
}

- (void)didWriteValueForCharacteristic:(CBCharacteristic *)characteristic forDevice:(RigLeBaseDevice *)device
{
    
}

@end
