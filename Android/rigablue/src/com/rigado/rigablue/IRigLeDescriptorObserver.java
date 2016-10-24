package com.rigado.rigablue;


import android.bluetooth.BluetoothGattDescriptor;

public interface IRigLeDescriptorObserver {

    void didWriteDescriptor(RigLeBaseDevice device, BluetoothGattDescriptor descriptor);

    void didReadDescriptor(RigLeBaseDevice device, BluetoothGattDescriptor descriptor);
}
