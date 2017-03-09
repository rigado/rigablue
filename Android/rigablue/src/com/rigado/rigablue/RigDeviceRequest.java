package com.rigado.rigablue;

/**
 *  RigDeviceRequest.java
 *
 *  @copyright (c) Rigado, Inc. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for a copy.
 */

/**
 * This class provides a wrapper for data pertaining to device requests.  Objects of this class
 * contain search paramaters that are then supplied to the RigLeDiscoveryManager.
 *
 * @see RigLeDiscoveryManager
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigDeviceRequest {

    /**
     * The array of UUIDs that will be searched for during discovery.
     */
    private String[] mUuidList;

    /**
     * Controls the length of time for discovery.  If set to 0, then discovery will run forever.
     * The timeout is specified in milliseconds.
     */
    private int mTimeout;

    /**
     * The object responding to notifications from the discovery manager of discover events should
     * implement the IRigLeDiscoveryManagerObserver interface and should be set as the observer
     * for this object.
     * @see IRigLeDiscoveryManagerObserver
     */
    private IRigLeDiscoveryManagerObserver mObserver;

    /**
     * @return Returns the list of UUIDs for this request
     */
    public String[] getUuidList() {
        return mUuidList;
    }

    /**
     * @return Returns the length of time, in milliseconds, for which the discovery should be
     * performed.
     */
    public int getTimeout() {
        return mTimeout;
    }

    //TODO: Make constructor take observer as well

    /**
     * Creates a new RigDeviceRequest object
     *
     * @param uuidList The list of UUIDs to search for during discovery
     * @param timeout The length of time, in milliseconds, for which discovery should run using this
     *                request object.
     */
    public RigDeviceRequest(String[] uuidList, int timeout) {
        mUuidList = uuidList;
        mTimeout = timeout;
    }

    /**
     * @return Returns the current discovery observer object
     */
    public IRigLeDiscoveryManagerObserver getObserver() {
        return mObserver;
    }

    /**
     * Sets the discovery observer object for this request
     * @param observer The observer for this request
     */
    public void setObserver(IRigLeDiscoveryManagerObserver observer) {
        mObserver = observer;
    }
}
