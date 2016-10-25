package com.rigado.rigablue;


import android.bluetooth.BluetoothGattDescriptor;

public interface IRigLeDescriptorObserver {
    void didReadDescriptor(RigLeBaseDevice device, BluetoothGattDescriptor descriptor);
}
