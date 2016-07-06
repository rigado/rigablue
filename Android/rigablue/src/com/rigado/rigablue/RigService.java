/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rigado.rigablue;

import android.bluetooth.*;
import android.content.Context;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 *  RigService.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * Service class for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigService {
    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private HashMap<String, BluetoothGatt> mBluetoothGattHashMap;
    private HashMap<String, BluetoothGattCallback> mBluetoothGattCallbackHashMap;
    private IRigCoreListener mRigCoreListener;

    /**
     * Constructs a RigService object.
     * @param context The application context for this object
     * @param iRigCoreListener The observer of this object
     */
    public RigService(Context context, IRigCoreListener iRigCoreListener) {
        mContext = context;
        mRigCoreListener = iRigCoreListener;
    }

    /**
     * Clears the device cache for the low level bluetooth operations.
     * @param gatt The Gatt object for which to clear the cache
     * @return  If the refresh method is found, the cache clear result is returned; false otherwise.
     */
    private boolean refreshDeviceCache(BluetoothGatt gatt){
        RigLog.d("refreshDeviceCache");
        try {
            Method localMethod = gatt.getClass().getMethod("refresh", new Class[0]);
            if (localMethod != null) {
                boolean bool = ((Boolean) localMethod.invoke(gatt, new Object[0])).booleanValue();
                return bool;
            }
        }
        catch (Exception localException) {
            RigLog.e("An exception occurred while refreshing device");
        }
        return false;
    }

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        RigLog.d("initialize");
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.

        mBluetoothGattHashMap = new HashMap<>();
        mBluetoothGattCallbackHashMap = new HashMap<>();
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                RigLog.e("Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            RigLog.e("Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    /**
     * Returns the current connection state for the device
     * @param device The device for which to retrieve the connection state
     * @return The connection state
     */
    public int getConnectionStateForDevice(BluetoothDevice device) {
        BluetoothGatt gatt = mBluetoothGattHashMap.get(device.getAddress());
        return gatt.getConnectionState(device);
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     * @return Return true if the connection is initiated successfully. The connection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public synchronized boolean connect(final String address) {
        RigLog.d("connect");
        if (mBluetoothAdapter == null || address == null) {
            RigLog.i("BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if(device == null) {
            RigLog.e("Could not get remote device!");
            return false;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean isAlreadyConnected = false;

                if (mBluetoothGattHashMap.containsKey(address)) {
                    BluetoothDevice btDevice = mBluetoothGattHashMap.get(address).getDevice();
                    if (mBluetoothManager.getConnectionState(btDevice,
                            BluetoothProfile.GATT_SERVER) == BluetoothProfile.STATE_CONNECTED) {
                        RigLog.w("Device already connected.");
                        isAlreadyConnected = true;
                    }
                }

                if(!isAlreadyConnected) {
                    RigLog.d("Trying to create a new connection.");
                    BluetoothGattCallback callback = new RigBluetoothGattCallback(mRigCoreListener, mBluetoothGattHashMap, mBluetoothGattCallbackHashMap);
                    if (mBluetoothGattCallbackHashMap.containsKey(address)) {
                        mBluetoothGattCallbackHashMap.remove(address);
                        mBluetoothGattCallbackHashMap.put(address, callback);
                    }

                    BluetoothGatt gatt = device.connectGatt(mContext, false, callback);
                    mBluetoothGattHashMap.put(address, gatt);
                    if (gatt != null) {
                        refreshDeviceCache(gatt);
                    }
                }
            }
        }).start();
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     * @param address The address of the destination device.
     */
    public synchronized void disconnect(final String address) {
        RigLog.d("disconnect");
        if (mBluetoothAdapter == null) {
            RigLog.e("BluetoothAdapter not initialized");
            return;
        }

        if (mBluetoothGattHashMap.get(address) == null) {
            RigLog.w("No outstanding connection request or active connection to " + address);
            return;
        }
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                BluetoothGatt gatt = mBluetoothGattHashMap.get(address);
                if(gatt != null) {
                    mBluetoothGattHashMap.get(address).disconnect();

                }
            }
        }).start();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public synchronized void close() {
        RigLog.d("close");
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (BluetoothGatt bluetoothGatt : mBluetoothGattHashMap.values()) {
                    if (bluetoothGatt != null) {
                        bluetoothGatt.close();
                    }
                }
                mBluetoothGattHashMap.clear();
            }
        }).start();
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param address The address of the destination device.
     * @param characteristic The characteristic to read
     */
    public synchronized void readCharacteristic(final String address, final BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
            RigLog.e("BluetoothAdapter not initialized or device already disconnected");
            return;
        }

        if(characteristic == null) {
            RigLog.e("Invalid characteristic; Characteristic is null!");
            return;
        }

        RigLog.d("readCharacteristic - " + Arrays.toString(characteristic.getValue()));
        mBluetoothGattHashMap.get(address).readCharacteristic(characteristic);
    }

    /**
     * Request a write on a the {@code BluetoothGattCharacteristic}. The write result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param address The address of the destination device.
     * @param characteristic The characteristic to write
     */
    public synchronized void writeCharacteristic(final String address, final BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
            RigLog.w("BluetoothAdapter not initialized or device already disconnected");
            return;
        }

        if(characteristic == null) {
            RigLog.e("Invalid characteristic; Characteristic is null!");
            return;
        }

        RigLog.i("writeCharacteristic for " + address + " with value - " + Arrays.toString(characteristic.getValue()));

        if(!mBluetoothGattHashMap.get(address).writeCharacteristic(characteristic)) {
            RigLog.e("writeCharacteristic failed!");
        }
    }

    private static final UUID CLIENT_CHARACTERISTIC_CONFIGURATION =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /**
     * Enables or disables notification on a the characteristic.
     *
     * @param address The address of the destination device.
     * @param characteristic Characteristic to act on
     * @param enabled        If true, enable notification; false otherwise
     */
    public synchronized void setCharacteristicNotification(final String address, final BluetoothGattCharacteristic characteristic, final boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGattHashMap.get(address) == null) {
            RigLog.w("BluetoothAdapter not initialized or device already disconnected");
            return;
        }

        if(characteristic == null) {
            RigLog.e("Invalid characteristic; Characteristic is null!");
            return;
        }

        RigLog.d("setCharacteristicNotification - " + Arrays.toString(characteristic.getValue()));
        mBluetoothGattHashMap.get(address).setCharacteristicNotification(characteristic, enabled);
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION);
        if (descriptor != null) {
            RigLog.d("descriptor = " + descriptor.getUuid());
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGattHashMap.get(address).writeDescriptor(descriptor);
        } else {
            RigLog.w("descriptor = null");
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @param address The address of the destination device.
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices(final String address) {
        if (mBluetoothGattHashMap.get(address) == null) {
            return null;
        }
        return mBluetoothGattHashMap.get(address).getServices();
    }
}
