package com.rigado.rigablue;

/**
 *  IRigDataRequest.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This interface provides a function for post operation requests to the low level Bluetooth
 * functions.  It should not be directly implemented by applications using Rigablue.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public interface IRigDataRequest {
    /**
     * This method is called to post a message to the RigService object connected to the Bluetooth
     * device that should receive the operation.
     *
     * @param service The service for the Bluetooth device object
     */
    void post(RigService service);
}
