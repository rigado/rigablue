//
//  RigFirmwareUpdateRequest.m
//  Rigablue
//
//  Created by Eric P. Stutzenberger on 9/17/15.
//  Copyright © 2017 Rigado, Inc. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for a copy.

#import "RigFirmwareUpdateRequest.h"
#import "RigLeBaseDevice.h"

@implementation RigFirmwareUpdateRequest
+ (RigFirmwareUpdateRequest*) updateRequestWithDevice:(RigLeBaseDevice*)device image:(NSData*)image activationChar:(CBCharacteristic*)activateChar activationCommand:(NSData*)activateCommand maxRssi:(int)rssi
{
    RigFirmwareUpdateRequest *request = [[RigFirmwareUpdateRequest alloc] init];
    request.updateDevice = device;
    request.image = image;
    request.activationCharacteristic = activateChar;
    request.activationCommand = activateCommand;
    request.maxRssi = rssi;
    
    return request;
}

@end
