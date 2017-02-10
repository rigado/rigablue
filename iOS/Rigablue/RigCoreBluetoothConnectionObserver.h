//
//  @file RigCoreBluetoothConnectionObserver.h
//  @library Rigablue
//
//  Created by Eric Stutzenberger on 4/18/14.
//  Copyright © 2017 Rigado, Inc. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for a copy.

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@protocol RigCoreBluetoothConnectionObserver <NSObject>
- (void)didConnectDevice:(CBPeripheral*)peripheral;
- (void)connectionDidTimeout:(CBPeripheral*)peripheral;
- (void)didDisconnectDevice:(CBPeripheral*)peripheral;
- (void)didFailToConnectDevice:(CBPeripheral*)peripheral;
@end
