//
//  RigFirmwareUpdateRequest.h
//  Rigablue
//
//  Created by Eric P. Stutzenberger on 9/17/15.
//  Copyright © 2017 Rigado, Inc. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for a copy.

#import <Foundation/Foundation.h>
#import "RigLeBaseDevice.h"

@interface RigFirmwareUpdateRequest : NSObject

+ (RigFirmwareUpdateRequest*) updateRequestWithDevice:(RigLeBaseDevice*)device image:(NSData*)image activationChar:(CBCharacteristic*)activateChar activationCommand:(NSData*)activateCommand maxRssi:(int)rssi;

@property (nonatomic, strong) RigLeBaseDevice *updateDevice;
@property (nonatomic, strong) NSData *image;
@property (nonatomic, strong) CBCharacteristic *activationCharacteristic;
@property (nonatomic, strong) NSData *activationCommand;
@property int maxRssi;

@end
