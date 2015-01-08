//
//  RigFirmwareUpdateManager.m
//  Rigablue Library
//
//  Created by Eric Stutzenberger on 11/8/13.
//  @copyright (c) 2013-2014 Rigado, LLC. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for for a copy.

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
#define IMAGE_SECURE_DATA_START                 IMAGE_INIT_PACKET_IDX + IMAGE_INIT_PACKET_SIZE

/* Operation Codes for controlling the DFU */
#define DFU_START                               1
#define INITIALIZE_DFU                          2  /* Available but not used */
#define RECEIVE_FIRMWARE_IMAGE                  3
#define VALIDATE_FIRMWARE_IMAGE                 4
#define ACTIVATE_FIRMWARE_AND_RESET             5
//#define SYSTEM_RESET                            6  /* Available but not used */
#define ERASE_AND_RESET                         9
#define ERASE_SIZE_REQUEST                      10

#define RESPONSE                                16
#define PACKET_RECEIVED_NOTIFICATION_REQUEST    8
#define NUMBER_OF_PACKETS                       1

#define PACKET_RECEIVED_NOTIFICATION            17
#define RECEIVED_OPCODE                         16

#define OPERATION_SUCCESS                       1

/* Device Packet size */
#define BYTES_IN_ONE_PACKET                     20

typedef enum FirmwareManagerState_enum
{
    /* Initial state of update manager */
    State_Init,
    /* This state is used just before discovering services on the device */
    State_DiscoverFirmwareServiceCharacteristics,
    /* This state is used to check how much has been erased on the device */
    State_CheckEraseAfterReset,
    /* This state is set after the flash has been successfully erased */
    State_ReconnectAfterInitialFlashErase,
    /* This state is used during image transfer */
    State_TransferringRadioImage,
    /* This is the final state once the transfer is complete */
    State_FinishedRadioImageTransfer
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
    BOOL shouldWaitForErasedSize;
    BOOL didDisconnectToErase;
    BOOL didForceEraseAfterStmUpdateImageRan;
    
    uint32_t totalPackets;
    uint32_t packetNumber;
    uint32_t totalBytesSent;
    uint32_t totalBytesErased;
    uint8_t lastPacketSize;
    
    id<RigLeConnectionManagerDelegate> oldDelegate;
    RigLeBaseDevice *bootloaderDevice;
}

@synthesize delegate;

- (id)init
{
    self = [super init];
    if (self) {
        isFileSizeWritten = NO;
        isPacketNotificationEnabled = NO;
        isReceivingFirmwareImage = NO;
        isLastPacket = NO;
        shouldStopSendingPackets = NO;
        didForceEraseAfterStmUpdateImageRan = NO;
        
        delegate = nil;
        state = State_Init;
        imageSize = 0;
        image = nil;
    }
    return self;
}

- (BOOL)updateFirmware:(RigLeBaseDevice*)device Image:(NSData*)firmwareImage ImageSize:(uint32_t)firmwareImageSize activateChar:(CBCharacteristic*)characteristic
       activateCommand:(uint8_t*)command activateCommandLen:(uint8_t)commandLen
{
    NSLog(@"__updateFirmware__");
    
    // Create the firmware update service object and assigned this object as the delegate
    firmwareUpdateService = [[RigFirmwareUpdateService alloc] init];
    firmwareUpdateService.delegate = self;
    [firmwareUpdateService setDevice:device];
    
    //Intialize firmware image variables
    imageSize = firmwareImageSize;
    image = firmwareImage;
    
    //Set to automatically reconnect.  This will force iOS to connect again immediately after receving an advertisement packet from the peripheral after
    //activating the bootloader.
    firmwareUpdateService.shouldReconnectToPeripheral = YES;
    state = State_Init;
    
    //If already connected to a DFU, then start the update, otherwise send Bootloader activation command
    state = State_DiscoverFirmwareServiceCharacteristics;
    //TODO: iOS does strange things with the advertised name, it is probably better to check on discovered services to see if they match the DFU
    if ([device.name isEqualToString:@"RigDfu"]) {
        if (device.peripheral.state == CBPeripheralStateConnected) {
            if (!device.isDiscoveryComplete) {
                [firmwareUpdateService triggerServiceDiscovery];
            } else {
                [firmwareUpdateService determineSecureDfuStatus];
                [firmwareUpdateService enableControlPointNotifications];
            }
        } else {
            [firmwareUpdateService connectPeripheral];
        }
    } else {
        if (characteristic == nil || device == nil || device.peripheral == nil || command == nil) {
            NSLog(@"Invalid parameter provided!");
            return NO;
        }
        [device.peripheral writeValue:[NSData dataWithBytes:command length:commandLen] forCharacteristic:characteristic type:CBCharacteristicWriteWithoutResponse];
        
        RigLeDiscoveryManager *dm = [RigLeDiscoveryManager sharedInstance];
        
        firmwareUpdateService.shouldReconnectToPeripheral = NO;
        CBUUID *dfuServiceUuid = [CBUUID UUIDWithString:kupdateDFUServiceUuidString];
        NSArray *uuidList = [NSArray arrayWithObject:dfuServiceUuid];
        RigDeviceRequest *dr = [RigDeviceRequest deviceRequestWithUuidList:uuidList timeout:DFU_SEARCH_TIMEOUT delegate:self allowDuplicates:NO];
        [dm discoverDevices:dr];
        [delegate updateStatus:@"Searching for Update Service..." errorCode:DfuError_None];
    }
    
    return YES;
}

- (void)updateDeviceAndTriggerDiscovery
{
    firmwareUpdateService.shouldReconnectToPeripheral = YES;
    [firmwareUpdateService setDevice:bootloaderDevice];
    [firmwareUpdateService triggerServiceDiscovery];
}

/**
 *  Simply sends the size of the firmware image to the DFU.  This is expect after enabling packet notifications through a command to the control point.
 */
- (void)writeFileSize
{
    NSLog(@"__writeFileSize__");
    //uint32_t fileSize = LightControllerImageSize;
    
    if ([firmwareUpdateService isSecureDfu]) {
        /* For the secure update, the start packet is stored within the first 12 bytes of the signed binary */
        uint8_t data[12];
        memcpy(data, &image.bytes[0], sizeof(data));
        /* EPS - Noticed that breakpoing here kept update from failing.  Invesitgate further later!! */
        [NSThread sleepForTimeInterval:1.0f];
        
        [firmwareUpdateService writeDataToPacketCharacteristic:data withLen:sizeof(data) shouldGetResponse:NO];
        [delegate updateStatus:@"Writing Device Update Size and Type" errorCode:0];
        
        /* Adjust image size to account for data at the beginning of the image */
        imageSize -= (IMAGE_START_PACKET_SIZE + IMAGE_INIT_PACKET_SIZE);
    }
    else
    {
        uint8_t data[4];
        data[0] = imageSize & 0xFF;
        data[1] = (imageSize >> 8) & 0xFF;
        data[2] = (imageSize >> 16) & 0xFF;
        data[3] = (imageSize >> 24) & 0xFF;
        
        /* EPS - Noticed that breakpoing here kept update from failing.  Invesitgate further later!! */
        [NSThread sleepForTimeInterval:1.0f];
        
        [firmwareUpdateService writeDataToPacketCharacteristic:data withLen:sizeof(data) shouldGetResponse:NO];
        [delegate updateStatus:@"Writing Device Update Size" errorCode:0];
    }
}

/**
 *  Enables notifications to be sent back as packets are received.  Note that this is a notification sent from the DFU on the control point
 *  and not an enablement of notifications on the Packet characteristic.
 */
- (void)enablePacketNotifications
{
    NSLog(@"__enablePacketNotifications__");
    uint8_t data[] = { PACKET_RECEIVED_NOTIFICATION_REQUEST, NUMBER_OF_PACKETS, 0 };
    
    [firmwareUpdateService writeDataToControlPoint:data withLen:sizeof(data) shouldGetResponse:YES];
    [delegate updateStatus:@"Enabling Notifications" errorCode:0];
}

- (void)sendInitPacket
{
    NSLog(@"__sendInitPacket__");
    uint8_t initPacket[IMAGE_INIT_PACKET_SIZE];
    memcpy(initPacket, &image.bytes[IMAGE_INIT_PACKET_IDX], IMAGE_INIT_PACKET_SIZE);
    
    [firmwareUpdateService writeDataToPacketCharacteristic:initPacket withLen:IMAGE_INIT_PACKET_SIZE/2 shouldGetResponse:NO];
    [NSThread sleepForTimeInterval:0.100f];
    [firmwareUpdateService writeDataToPacketCharacteristic:&initPacket[IMAGE_INIT_PACKET_SIZE/2] withLen:IMAGE_INIT_PACKET_SIZE/2 shouldGetResponse:NO];
}

/**
 *  Send this command prepares the DFU to receive the firmware image.
 */
- (void)receiveFirmwareImage
{
    NSLog(@"__receiveFirmwareImage__");
    uint8_t data = RECEIVE_FIRMWARE_IMAGE;
    [firmwareUpdateService writeDataToControlPoint:&data withLen:sizeof(data) shouldGetResponse:YES];
}

/**
 *  Initiates send firmware data to the DFU.
 */
- (void)startUploadingFile
{
    NSLog(@"__startUploadingFile__");
    totalPackets = (imageSize / BYTES_IN_ONE_PACKET);
    if (imageSize % BYTES_IN_ONE_PACKET) {
        totalPackets++;
    }
    
    [self deterimeLastPacketSize];
    
    [delegate updateStatus:@"Transferring New Device Software" errorCode:0];
    [self sendPacket];
}

/**
 *  Determines the size of the last packet that will be sent.  The firmware image is transferred in equal size packets,
 *  usually 20 bytes in size to minimize transfer time, except for the last packet if necessary.
 */
- (void)deterimeLastPacketSize
{
    if ((imageSize % BYTES_IN_ONE_PACKET) == 0) {
        lastPacketSize = BYTES_IN_ONE_PACKET;
    } else {
        lastPacketSize = (imageSize - ((totalPackets - 1) * BYTES_IN_ONE_PACKET));
    }
    
    NSLog(@"Last Packet Size: %d", lastPacketSize);
}

/**
 *  Sends one packet of data to the DFU.  If the packet being sent is the last packet, then the size
 *  is based on the size calculated in the call to determineLastPacketSize.
 */
- (void)sendPacket
{
    //NSLog(@"__sendPacket__");
    packetNumber++;
    uint8_t packetSize = BYTES_IN_ONE_PACKET;
    
    /* Handle last packet */
    if (packetNumber == totalPackets) {
        NSLog(@"Sending last packet: %d", packetNumber);
        isLastPacket = YES;
        /* Adjust size for last packet */
        packetSize = lastPacketSize;
    } else {
        NSLog(@"Sending packet: %d/%d Bytes Sent: %d/%d", packetNumber, totalPackets, packetNumber * 20, imageSize);
    }
    
    NSRange range;
    
    if ([firmwareUpdateService isSecureDfu]) {
        range = NSMakeRange(IMAGE_SECURE_DATA_START + (packetNumber - 1) * BYTES_IN_ONE_PACKET, packetSize);
    } else {
        range = NSMakeRange((packetNumber - 1) * BYTES_IN_ONE_PACKET, packetSize);
    }
    
    NSData *dataToSend = [image subdataWithRange:range];
    [firmwareUpdateService writeDataToPacketCharacteristic:dataToSend.bytes withLen:dataToSend.length shouldGetResponse:NO];
}

/**
 *  Sends the command to cause the DFU to validate the firmware.
 */
- (void)validateFirmware
{
    NSLog(@"__validateFirmware__");
    uint8_t cmd = VALIDATE_FIRMWARE_IMAGE;
    [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    [delegate updateStatus:@"Validating Transferred Device Software" errorCode:0];
}

/**
 *  Sends the command to activate the newly uploaded firmware.  This will cause the device to exit the DFU and start the application.
 */
- (void)activateFirmware
{
    NSLog(@"__activateFirmware__");
    uint8_t cmd = ACTIVATE_FIRMWARE_AND_RESET;
    
    /* Reassign delegate prior to activation since a disconnect occurs after activation */
    firmwareUpdateService.shouldReconnectToPeripheral = NO;
    firmwareUpdateService.alwaysReconnectOnDisconnect = NO;
    [firmwareUpdateService completeUpdate];
    
    [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    [delegate updateStatus:@"Activating Updated Device Software" errorCode:0];
}

#pragma mark - LeFirmwareUpdateServiceDelegate Methods
- (void)didConnectPeripheral
{
    //Sent as a delegate method but not needed for this implementation
    [firmwareUpdateService triggerServiceDiscovery];
}

/**
 *  This method is sent from the delegate protocol once device service and characteristic discovery is complete.
 */
- (void)didDiscoverCharacteristicsForDFUSerivce
{
//    RigDfuError_t error;
//    if (state == State_DiscoverFirmwareServiceCharacteristics) {
//        state = State_CheckEraseAfterReset;
//        //Enable control point notifications which will perform an erase check after
//        //notifications are successfully enabled.
//        error = [firmwareUpdateService enableControlPointNotifications];
//        if (error != DfuError_None) {
//            [delegate updateStatus:@"Dfu Error: " errorCode:error];
//        }
//    } else if(state == State_ReconnectAfterInitialFlashErase) {
//        //Reconnection after erasing the flash has occurred and discovery is complete.  Update the state
//        //so that after checking the erase size (which should be the size of the firmware image), we will
//        //initial transfer of the firmware image.
//        state = State_TransferringRadioImage;
//        error = [firmwareUpdateService enableControlPointNotifications];
//        if (error != DfuError_None) {
//            [delegate updateStatus:@"Dfu Error: " errorCode:error];
//        }
//    }
    if (state == State_TransferringRadioImage) {
        state = State_Init;
        isFileSizeWritten = NO;
        isPacketNotificationEnabled = NO;
        isReceivingFirmwareImage = NO;
        [delegate updateProgress:0.0f];
        packetNumber = 0;
    }
    [firmwareUpdateService enableControlPointNotifications];
    
}

/**
 *  This is called after successfully enabling notifications for the control point
 */
- (void)didEnableControlPointNotifications
{
    NSLog(@"__didEnableControlPointNotifications__");
    // This is sent after enabling notifications on the control point
    // This is always done after discovery has completed.  Next we send an erase size request.
    // If the result of this request is at least the size of the firmware image, then we are
    // certain flash has been erased and we can send over the new firmware image.  Otherwise, it
    // will trigger the flash erase.
//    uint8_t data = ERASE_SIZE_REQUEST;
//    
//    shouldWaitForErasedSize = YES;
    uint8_t cmd = DFU_START;
    NSLog(@"Sending DFU_START opcode");
    shouldWaitForErasedSize = NO;
    state = State_TransferringRadioImage;
    [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
    //[firmwareUpdateService writeDataToControlPoint:&data withLen:1 shouldGetResponse:YES];
    [delegate updateStatus:@"Starting Device Firmware Update" errorCode:DfuError_None];
}

/**
 *  This is called after succussfully writing data to the control point with a WriteWithResponse attribute
 */
- (void) didWriteValueForControlPoint
{
    NSLog(@"__didWriteValueForControlPoint__");
    //This check ensures none of the above is performed until after we successfully erase the flash.
    if (shouldWaitForErasedSize) {
        return;
    }
    
    if (state == State_FinishedRadioImageTransfer) {
        //If the firmware update is now complete, finalize the update and notify the app.
        [NSThread sleepForTimeInterval:2.0];
        [delegate didFinishUpdate];
    }
    
    //This functionality behaves in tandem with the commands being written to the control point
    //from didUpdateValueForControlPoint
    if (!isFileSizeWritten) {
        [self writeFileSize];
    } else if(!isInitPacketSent && [firmwareUpdateService isSecureDfu]) {
        [self sendInitPacket];
    } else if(!isPacketNotificationEnabled) {
        isPacketNotificationEnabled = YES;
        [self receiveFirmwareImage];
    } else if(!isReceivingFirmwareImage) {
        isReceivingFirmwareImage = YES;
        [self startUploadingFile];
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
            //This is received after sending the size of the firmware image to the packet characteristic once
            //the device has been properly erased by the DFU.  Here, we mark the fact that the firmware image
            //size has been sent and enable notifications for packets.  This will cause a didWriteValueToCharacteristic
            //call to be generated by CoreBluetooth which will then cause the firmware image transfer to begin.
            if ([firmwareUpdateService isSecureDfu]) {
                isFileSizeWritten = YES;
                uint8_t cmd = INITIALIZE_DFU;
                [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
            } else {
                [self enablePacketNotifications];
            }
        }
    } else if(opCode == RECEIVED_OPCODE && request == INITIALIZE_DFU) {
        NSLog(@"Received Notification for INITIALIZE_DFU");
        if(value[2] == OPERATION_SUCCESS) {
            //This is received after sending the initialization information.  It is only necessary for the secure
            //device firmware update.  If this is not the secure dfu, then this step is skipped.
            isInitPacketSent = YES;
            [self enablePacketNotifications];
        }
    }
    else if (opCode == PACKET_RECEIVED_NOTIFICATION) {
        //This is sent every time a packet is successfully received by the DFU.  This provides the app a way of
        //knowing that each packet has been received and the total size that has been transferred thus far.
        totalBytesSent = (value[1] + (value[2] << 8) + (value[3] << 16) + (value[4] << 24)) & 0xFFFFFFFF;
        
        [delegate updateProgress:(float)((float)totalBytesSent / (float)imageSize)];
        
        //If we haven't sent the last packet yet, then keep sending packets.  Once sent, we will notify the app
        //that the firmware image has been fully transferred.
        if (!isLastPacket && !shouldStopSendingPackets) {
            [self sendPacket];
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
            [self validateFirmware];
        } else {
            [delegate updateStatus:@"Error during transfer" errorCode:value[2]];
            NSLog(@"Error on firmware transfer: %d", value[2]);
        }
    } else if (opCode == RECEIVED_OPCODE && request == VALIDATE_FIRMWARE_IMAGE) {
        NSLog(@"Firmware validated");
        if (value[2] == OPERATION_SUCCESS) {
            NSLog(@"Succesful verification and transfer of firmware!");
            
            //Once the firmware is validated, the firmware transfer is considered successful and the activation command
            //is sent to the DFU.
            if(state == State_TransferringRadioImage) {
                [delegate updateStatus:@"Device Software Validated Successfully!" errorCode:0];
                state = State_FinishedRadioImageTransfer;
                [self activateFirmware];
            }
        } else {
            NSLog(@"Error occurred during firmware validation");
            [delegate updateStatus:@"Error on validitation" errorCode:value[2]];
        }
    } else if (opCode == RECEIVED_OPCODE && request == ERASE_SIZE_REQUEST) {
        if (value[2] == OPERATION_SUCCESS) {
            //This message is sent by the DFU after an erase size request.  Initially, if not earsed, the erase
            //size request will return 0.  This will trigger the firmware update manager to send an erase and
            //reset command to the device which will then cause the device to disconnect and perform the
            //erase.  Once that is complete, the update manager will reconnect, perform discovery, and then send
            //another erase size request.  At that point, the erase size should be greater than the size of the
            //image to be sent.  If this is the case, then the firmware update is started by sending the DFU Start
            //command.  Once this command is written successfully, the didWriteValueToCharacteristic callback will
            //cause the firmware image size to be written to the device.  Once received, this will trigger the DFU
            //to send the DFU Start response operation code and firmware transfer will begin.  See the first if
            //condition in this function.
            totalBytesErased = (value[3] + (value[4] << 8) + (value[5] << 16) + (value[6] << 24)) & 0xFFFFFFFF;
            if (totalBytesErased < imageSize) {
                
                uint8_t cmd = ERASE_AND_RESET;
                NSLog(@"Sending ERASE_AND_RESET opcode");
                if (state == State_CheckEraseAfterReset) {
                    state = State_ReconnectAfterInitialFlashErase;
                }
                firmwareUpdateService.shouldReconnectToPeripheral = YES;
                [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
            } else {
                /* Device already erased, continue with firmware update */
                uint8_t cmd = DFU_START;
                NSLog(@"Sending DFU_START opcode");
                shouldWaitForErasedSize = NO;
                if (state == State_CheckEraseAfterReset) {
                    state = State_TransferringRadioImage;
                }
                [firmwareUpdateService writeDataToControlPoint:&cmd withLen:sizeof(cmd) shouldGetResponse:YES];
            }
        }
    }
}

#pragma mark -
#pragma mark - RigLeDiscoveryManagerDelegate methods
- (void)didDiscoverDevice:(RigAvailableDeviceData *)device
{
    if ([device.peripheral.name isEqual:@"RigDfu"]) {
        oldDelegate = [RigLeConnectionManager sharedInstance].delegate;
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
    
}

#pragma mark -
#pragma mark - RigLeConnectionManagerDelegrate methods
-(void)didConnectDevice:(RigLeBaseDevice *)device
{
    bootloaderDevice = device;
    [RigLeConnectionManager sharedInstance].delegate = oldDelegate;
    [self performSelectorOnMainThread:@selector(updateDeviceAndTriggerDiscovery) withObject:nil waitUntilDone:NO];
}

-(void)didDisconnectPeripheral:(CBPeripheral *)peripheral
{
    NSLog(@"Connection failed!");
}

- (void)deviceConnectionDidFail:(RigAvailableDeviceData *)device
{
    
}

- (void)deviceConnectionDidTimeout:(RigAvailableDeviceData *)device
{
    
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


