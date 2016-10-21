package com.rigado.rigablue;


import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattDescriptor;

public class RigDescriptorRequest implements IRigDataRequest {

    BluetoothDevice mDevice;
    BluetoothGattDescriptor mDescriptor;

    public RigDescriptorRequest(BluetoothDevice device,
                                BluetoothGattDescriptor descriptor) {
        this.mDevice = device;
        this.mDescriptor = descriptor;
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public BluetoothGattDescriptor getDescriptor() {
        return mDescriptor;
    }

    @Override
    public void post(RigService service) {
        service.readDescriptor(mDevice.getAddress(),
                mDescriptor);
    }
}
