package com.rigado.rigablue;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattDescriptor;

public class RigDescriptorWriteRequest implements IRigDataRequest {
    private BluetoothDevice mDevice;
    private BluetoothGattDescriptor mDescriptor;
    private byte [] mValue;

    public RigDescriptorWriteRequest(BluetoothDevice device, BluetoothGattDescriptor descriptor,
                                     byte [] value) {
        this.mDevice = device;
        this.mDescriptor = descriptor;
        this.mValue = new byte[value.length];
        System.arraycopy(value, 0, mValue, 0, value.length);
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public BluetoothGattDescriptor getDescriptor() {
        return mDescriptor;
    }

    public byte[] getValue() {
        return mValue;
    }

    @Override
    public void post(RigService service) {
        if(service == null) {
            return;
        }

        mDescriptor.setValue(mValue);
        service.writeDescriptor(mDevice.getAddress(), mDescriptor);
    }
}
