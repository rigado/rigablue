//
//  @file RigLeBaseDevice.h
//  @library Rigablue 
//
//  Created by Eric Stutzenberger on 4/28/14.
//  @copyright (c) 2014 Rigado, LLC. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for for a copy.

#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

@class RigLeBaseDevice;

/**
 *  @protocol RigLeBaseDeviceDelegate
 *
 *  @discussion This protocol provides a delegate interface for notifying when certain device events occur.
 */
@protocol RigLeBaseDeviceDelegate <NSObject>

/**
 *  @method discoveryDidCompleteForDevice:
 *
 *  @param device The <code>RigLeBaseDevice</code> object for which discovery has finished.
 *
 *  @discussion This method is called when all services and characteristics have been discovered for a given device.
 */
- (void)discoveryDidCompleteForDevice:(RigLeBaseDevice*)device;

/**
 *  @method didUpdateValueForCharacteristic:
 *
 *  @param characteristic   The characteristic for while the value was read.  This is called when a read request is performed or when
 *                          a characteristic changes its value due to a notification from the peripheral.
 */
- (void)didUpdateValueForCharacteristic:(CBCharacteristic*)characteristic forDevice:(RigLeBaseDevice*)device;

- (void)didUpdateNotifyStateForCharacteristic:(CBCharacteristic*)characteristic forDevice:(RigLeBaseDevice*)device;

- (void)didWriteValueForCharacteristic:(CBCharacteristic*)characteristic forDevice:(RigLeBaseDevice*)device;
@end

/**
 *  @class RigLeBaseDevice
 *
 *  @discussion This class provides a base class for all connected peripherals.  It stores all services and characteristics for a connected <code>CBPeripheral</code>
 *              object.  The idea is for specific device classes to extend this class to implement required device functionality.
 */
@interface RigLeBaseDevice : NSObject

/** @name Creating Base Device Objects */
/**
 *  Initialize a base device object with the provide peripheral.  Use this rather than the base init method.
 *
 *  @param peripheral The peripheral to initiailize this object against.
 *
 *  @return Returns new base device oject.
 */
- (id)initWithPeripheral:(CBPeripheral*)peripheral;

/**
 *  @name Discovering And Retrieving Devices
 */
/**
 *  @method runDiscovery
 *
 *  @discussion This method starts the process of discovering available device services.  Once complete, all characteristics will be discovered for each service.
 *              Once all services and characteristics have been discovered, the delegate will be notified with the <code>discoveryDidCompleteForDevice:</code> method.
 */
- (void)runDiscovery;

/**
 *  @method getServiceList
 *
 *  @discussion This method provides access to the <code>CBService</code> objects discovered during device discovery.  Characteristics can be accessed through these
 *              service objects.
 *
 *  @return List of discovered services.
 */
- (NSArray*)getSerivceList;

- (void)enableNotificationsForCharacteristic:(CBCharacteristic*)characteristic;

- (void)setAdvertisementData:(NSDictionary*)advData;
- (NSDictionary*)getAdvertisementData;

/**
 *  @name Base Device properties
 */
/**
 *  @property peripheral
 *
 *  @discussion The <code>CBPeripheral</code> object for this device.
 *
 */
@property (nonatomic, strong) CBPeripheral* peripheral;

/**
 *  @property name
 *
 *  @discussion The name field from the peripheral object represented by this device object.
 */
@property (nonatomic, strong) NSString *name;

/**
 *  @property isDiscoveryComplete
 *
 *  @discussion This boolean value denotes whether or not discovery of services and characteristics for this devices has been completed.
 */
@property (readonly) BOOL isDiscoveryComplete;

/**
 *  @property delegate
 *
 *  @discussion The delegate for this object.
 */
@property (nonatomic, weak) id<RigLeBaseDeviceDelegate> delegate;

@end
