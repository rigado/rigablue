package com.rigado.rigablue;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothProfile;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;

/**
 *  RigBluetoothGattCallback.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class is a callback method handler for BluetoothGatt events.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */

public class RigBluetoothGattCallback extends BluetoothGattCallback {

    private IRigCoreListener mRigCoreListener;
    private HashMap<String, BluetoothGatt> mBluetoothGattHashMap;

    public RigBluetoothGattCallback(IRigCoreListener listener, HashMap<String, BluetoothGatt> bluetoothGattHashMap,
                                    HashMap<String, BluetoothGattCallback> bluetoothGattCallbackHashMap) {
        mRigCoreListener = listener;
        mBluetoothGattHashMap = bluetoothGattHashMap;
    }

    @SuppressWarnings("unused")
	private boolean refreshDeviceCache(BluetoothGatt gatt){
        RigLog.d("refreshDeviceCache");
        try {
            Method localMethod = gatt.getClass().getMethod("refresh");
            if (localMethod != null) {
                return (boolean) localMethod.invoke(gatt);
            }
        }
        catch (Exception localException) {
            RigLog.e("An exception occurred while refreshing device");
        }
        return false;
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        RigLog.d("onReadRemoteRssi - " + status + ", rssi = " + rssi);
        if (mRigCoreListener != null) {
            mRigCoreListener.onActionGattReadRemoteRssi(gatt.getDevice(), rssi);
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        RigLog.d(String.format(Locale.US, "onConnectionStateChange : status  %d newState %d address %s",
                status, newState, gatt.getDevice().getAddress()));

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            if (!mBluetoothGattHashMap.containsKey(gatt.getDevice().getAddress())) {
                mBluetoothGattHashMap.put(gatt.getDevice().getAddress(), gatt);
            }
            if(status == BluetoothGatt.GATT_SUCCESS) {
                if (mRigCoreListener != null) {
                    mRigCoreListener.onActionGattConnected(gatt.getDevice());
                }

                // Attempts to discover services after successful connection.
                RigLog.d("Attempting to start service discovery:" + gatt.discoverServices());
            } else {
                mBluetoothGattHashMap.remove(gatt.getDevice().getAddress());
                mRigCoreListener.onActionGattFail(gatt.getDevice());
            }
            return;

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            gatt.close();
            if (mBluetoothGattHashMap.containsKey(gatt.getDevice().getAddress())) {
                mBluetoothGattHashMap.remove(gatt.getDevice().getAddress());
            }
            if (mRigCoreListener != null) {
                mRigCoreListener.onActionGattDisconnected(gatt.getDevice());
            }
            RigLog.d("Disconnected from GATT server.");
            return;
        }
        if (mRigCoreListener != null) {
            mRigCoreListener.onActionGattFail(gatt.getDevice());
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        RigLog.d("onServicesDiscovered");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (mRigCoreListener != null) {
                mRigCoreListener.onActionGattServicesDiscovered(gatt.getDevice());
            }
        } else {
            RigLog.d("onServicesDiscovered received: " + status);
        }
    }



    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        RigLog.d("onCharacteristicRead " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (mRigCoreListener != null) {
                mRigCoreListener.onActionGattDataAvailable(characteristic, gatt.getDevice());
            }
        } else {
            mRigCoreListener.onActionGattFail(gatt.getDevice());
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        RigLog.d("onCharacteristicChanged");
        if (mRigCoreListener != null) {
            mRigCoreListener.onActionGattDataNotification(characteristic, gatt.getDevice());
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic, int status) {
        RigLog.d("onCharacteristicWrite");
        if(status == BluetoothGatt.GATT_SUCCESS) {
            if(mRigCoreListener != null) {
                mRigCoreListener.onActionGattCharWrite(gatt.getDevice(), characteristic);
            }
        } else if(status == 133) {
                /* Send along anyway because we may have forced a reset to get in to the bootloader
                   and this can cause an invalid status return if the disconnection occurs before
                   this callback is registered.
                 */
            if(mRigCoreListener != null) {
                mRigCoreListener.onActionGattCharWrite(gatt.getDevice(), characteristic);
            }
        }
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                  int status) {
        RigLog.d("onDescriptorWrite");
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if(mRigCoreListener != null) {
                mRigCoreListener.onActionGattDescriptorWrite(descriptor, gatt.getDevice());
            }
        }
    }
}
