//
//  RigDfuError.h
//  Rigablue Library
//
//  Created by Eric Stutzenberger on 7/10/14.
//  Copyright © 2017 Rigado, Inc. All rights reserved.
//
//  Source code licensed under BMD-200 Software License Agreement.
//  You should have received a copy with purchase of BMD-200 product.
//  If not, contact info@rigado.com for a copy.

#ifndef Rigablue_RigDfuError_h
#define Rigablue_RigDfuError_h

//Error code for potential failure points within the firmware update manager
typedef enum DfuErrorEnum
{
    // No Error
    DfuError_None = 0,
    
    // Could not find RigDfu device. Bad Peripheral.
    DfuError_BadPeripheral = -1,
    
    // Could not initialize firmware update service. Missing control point characteristic.
    DfuError_ControlPointCharacteristicMissing = -2,
    
    // Could not find RigDfu device. Bad Device.
    DfuError_BadDevice = -3,
    
    // Could not find RigDfu device. Peripheral not set.
    DfuError_PeripheralNotSet = -4,
    
    // Invalid parameter in updateFirmware command.
    DfuError_InvalidParameter = -5,
    
    // Failed to validate firmware image.
    DfuError_ImageValidationFailure = -6,
    
    // Failed to activate firmware image.
    DfuError_ImageActivationFailure = -7,
    
    // CRC for the current firmware image does not match required CRC. Is it the correct patch version?
    DfuError_PatchCurrentImageCrcFailure = -8,
    
    // CRC for the updated firmware image does not match the required CRC. Is it the correct patch version?
    DfuError_PostPatchImageCrcFailure = -9,
    
    // Could not connect to RigDfu device.
    DfuError_CouldNotConnect = -10,
    
    // An unknown error occured.
    DfuError_Unknown = -11,
    
} RigDfuError_t;

#endif
