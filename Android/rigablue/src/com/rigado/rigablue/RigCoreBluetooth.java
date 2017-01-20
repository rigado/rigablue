package com.rigado.rigablue;

import android.annotation.TargetApi;
import android.bluetooth.*;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *  RigCoreBluetooth.java
 *
 *  @copyright (c) Rigado, LLC. All rights reserved.
 *
 *  Source code licensed under BMD-200 Software License Agreement.
 *  You should have received a copy with purchase of BMD-200 product.
 *  If not, contact info@rigado.com for for a copy.
 */

/**
 * This class manages interactions with the BluetoothGatt objects representing device connections.
 *
 * @author Eric Stutzenberger
 * @version 1.0
 */
public class RigCoreBluetooth implements IRigCoreListener {

    private static final String RigCoreBluetoothLibraryVersion = "Rigablue Library v" + BuildConfig.VERSION_NAME;

    private BluetoothAdapter mBluetoothAdapter;
    private Context mContext;
    private RigService mBluetoothLeService;
    private IRigCoreBluetoothConnectionObserver mConnectionObserver;
    private IRigCoreBluetoothDiscoveryObserver mDiscoveryObserver;
    private volatile boolean mIsDataOpInProgress;
    private volatile boolean mIsDiscovering;
    private UUID[] mUUIDList;
    private Queue<IRigDataRequest> mOpsQueue = new ConcurrentLinkedQueue<>();
    private BluetoothDevice mConnectingDevice;

    private static RigCoreBluetooth instance = null;

    private static final ScheduledExecutorService connectionWorker =
            Executors.newSingleThreadScheduledExecutor();
    private static final ScheduledExecutorService discoveryWorker =
            Executors.newSingleThreadScheduledExecutor();
    private static ScheduledFuture<?> mConnectionFuture;
    private static ScheduledFuture<?> mDiscoveryFuture;

    // Legacy BLE scanning
    private BluetoothAdapter.LeScanCallback mLegacyScanCallback = null;

    // BLE scanning for API 21+
    private BluetoothLeScanner mBleScanner = null;
    private ScanCallback mLollipopScanCallback = null;

    RigCoreBluetooth() {
        mContext = null;
        mIsDiscovering = false;
        mIsDataOpInProgress = false;
        mDiscoveryObserver = null;
        mConnectionObserver = null;
    }

    public static RigCoreBluetooth getInstance()
    {
        if(instance == null)
        {
            instance = new RigCoreBluetooth();
            RigLog.w(RigCoreBluetoothLibraryVersion);
        }
        return instance;
    }

    /**
     * Central initialization point for Rigablue. Call once Bluetooth has been enabled.
     * @param context
     */
    public static void initialize(Context context) {
        RigCoreBluetooth.getInstance().setContext(context);
        RigCoreBluetooth.getInstance().init();
    }

    public static String version() {
        return BuildConfig.VERSION_NAME;
    }

    void scheduleConnectionTimeout(long timeout) {

        Runnable task = new Runnable() {
            public void run() {
                if(mConnectionFuture != null
                        && !mConnectionFuture.isCancelled()
                        && getDeviceConnectionState(mConnectingDevice)!=BluetoothProfile.STATE_DISCONNECTED) {
                    RigLog.d("Connection timed out!");
                    disconnectPeripheral(mConnectingDevice);
                    mConnectionObserver.connectionDidTimeout(mConnectingDevice);
                }

            }
        };
        mConnectionFuture = connectionWorker.schedule(task, timeout, TimeUnit.MILLISECONDS);

    }

    void scheduleDiscoveryTimeout(long timeout) {

        Runnable task = new Runnable() {
            public void run() {
                if(mDiscoveryFuture != null) {
                    stopDiscovery();
                    mDiscoveryObserver.discoveryFinishedByTimeout();
                }
            }
        };
        mDiscoveryFuture = discoveryWorker.schedule(task, timeout, TimeUnit.MILLISECONDS);

    }

    /* Level can be any of the following -
     * Level 0 - Verbose and higher
     * Level 1 - Debug and higher
     * Level 2 - Information and higher
     * Level 3 - Warning and higher
     * Level 4+ - Errors only
     */
    public static void setLogLevel(int level) {
        RigLog.setLogLevel(level);
    }

    public void setContext(Context context)
    {
        this.mContext = context;
    }

    /**
     * The discovery flag is reset here to handle cases where a bluetooth state change is
     * received while discovery is running. 
     */
    public void init() {
        RigLog.d("__RigCoreBluetooth.init__");

        mIsDiscovering = false;
        mBluetoothLeService = new RigService(mContext, this);
        mBluetoothLeService.initialize();
        mContext.registerReceiver(mBluetoothStateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        final BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setUpLollipopScanCallback();
        } else {
            mLegacyScanCallback = new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
                    if (isRelevantScanRecord(scanRecord)) {
                        mDiscoveryObserver.didDiscoverDevice(device, rssi, scanRecord);
                        RigLog.i("Name: " + device.getName() + ". Address: " + device.getAddress());
                    }
                }
            };
        }
    }

    /**
     * Reset the flag when write/read/notify requests fail to prevent blocking the queue.
     */
    void requestDidFail() {
        RigLog.w("__RigCoreBluetooth.requestDidFail__");
        mIsDataOpInProgress = false;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setUpLollipopScanCallback() {
        mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mLollipopScanCallback = new ScanCallback() {
            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                RigLog.e("BLE Scan failed with error code " + errorCode);
            }

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                ScanRecord scanRecord = result.getScanRecord();
                if (scanRecord == null) {
                    return;
                }
                byte[] rawScanRecord = scanRecord.getBytes();
                if (isRelevantScanRecord(rawScanRecord)) {
                    mDiscoveryObserver.didDiscoverDevice(device, rssi, rawScanRecord);
                    RigLog.i("Name: " + device.getName() + ". Address: " + device.getAddress());
                }
            }
        };
    }

    public void finish() {
        RigLog.d("__RigCoreBluetooth.finish__");
        try {
            mContext.unregisterReceiver(mBluetoothStateReceiver);
        } catch (Exception ex) {
            RigLog.e(ex);
        }
        mBluetoothLeService.close();
    }

    public int getDeviceConnectionState(BluetoothDevice device) {
        final BluetoothManager bluetoothManager =
                (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        return bluetoothManager.getConnectionState(device, BluetoothGatt.GATT_SERVER);
    }

    void startDiscovery(final UUID[] uuidList, long timeout) {
        RigLog.d("__RigCoreBluetooth.startDiscovery__");
        if (!checkBluetoothState()) {
            return;
        }

        if(mIsDiscovering) {
            RigLog.e("Discovery started while already running!");
            return;
        }

        // Stops scanning after a pre-defined scan period.
        if (timeout > 0) {
            scheduleDiscoveryTimeout(timeout);
        }
        mIsDiscovering = true;
        mUUIDList = uuidList;

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Started on background thread; callbacks will fire on the main thread
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    startLollipopScan();
                } else {
                    startLegacyLeScan();
                }
            }
        }).start();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startLollipopScan () {
        // API 21+, except where noted 23+
        ScanSettings.Builder builder = new ScanSettings.Builder();
        // Scan using highest duty cycle (this is default for legacy).
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        builder.setReportDelay(0);

        // Aggressive mode seems to improve discovery in the short (15s) window we have for DFU
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE);
        }

        ScanSettings settings = builder.build();

        mBleScanner.startScan(null, settings, mLollipopScanCallback);
    }

    private void startLegacyLeScan () {
        mBluetoothAdapter.startLeScan(mLegacyScanCallback);
    }

    void stopDiscovery() {
        RigLog.d("__RigCoreBluetooth.stopDiscovery__");
        if (!checkBluetoothState()) {
            return;
        }

        mIsDiscovering = false;

        if((null != mDiscoveryFuture) && !mDiscoveryFuture.isDone()) {
            mDiscoveryFuture.cancel(true);
            mDiscoveryFuture = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mBleScanner != null) {
            mBleScanner.stopScan(mLollipopScanCallback);
        } else if (mBluetoothAdapter != null) {
            mBluetoothAdapter.stopLeScan(mLegacyScanCallback);
        }
    }

    void connectPeripheral(final BluetoothDevice device, long timeout) {
        RigLog.d("__RigCoreBluetooth.connectPeripheral__");
        if (!checkBluetoothState()) {
            return;
        }

        mConnectingDevice = device;
        mBluetoothLeService.connect(device.getAddress());
        scheduleConnectionTimeout(timeout);
    }

    void disconnectPeripheral(BluetoothDevice device) {
        RigLog.d("__RigCoreBluetooth.disconnectPeripheral__");
        clearQueue();
        if (!checkBluetoothState()) {
            return;
        }
        mBluetoothLeService.disconnect(device.getAddress());
    }

    List<BluetoothGattService> getServiceList(final String address) {
        return mBluetoothLeService.getSupportedGattServices(address);
    }

    public void readCharacteristic(BluetoothDevice device, BluetoothGattCharacteristic characteristic) {
        RigLog.d("__RigCoreBluetooth.readCharacteristic__");
        IRigDataRequest request = new RigReadRequest(device, characteristic);
        read(request);
    }

    public void writeCharacteristic(BluetoothDevice device, BluetoothGattCharacteristic characteristic,
                                    byte [] value) {
        RigLog.d("__RigCoreBluetooth.writeCharacteristic__");
        RigLog.d("Characteristic WriteType: " + characteristic.getWriteType());

        IRigDataRequest request = new RigWriteRequest(device, characteristic, value);
        write(request);
    }

    public void setCharacteristicNotification(BluetoothDevice device, BluetoothGattCharacteristic characteristic,
                                              boolean enableState) {
        RigLog.d("__RigCoreBluetooth.setCharacteristicNotification__");

        IRigDataRequest request = new RigNotificationStateChangeRequest(device, characteristic, enableState);
        write(request);
    }

    public void readDescriptor(BluetoothDevice device, BluetoothGattDescriptor descriptor) {
        RigLog.d("__RigCoreBluetooth.readDescriptor__");

        IRigDataRequest request = new RigDescriptorReadRequest(device, descriptor);
        read(request);
    }

    private synchronized void read(IRigDataRequest request) {
        if(mOpsQueue.isEmpty() && !mIsDataOpInProgress) {
            mIsDataOpInProgress = true;
            doOp(request);
        } else {
            mOpsQueue.add(request);
        }
    }

    private synchronized void write(IRigDataRequest request) {
        if(mOpsQueue.isEmpty() && !mIsDataOpInProgress) {
            mIsDataOpInProgress = true;
            doOp(request);
        } else {
            if(request instanceof RigWriteRequest) {
                RigLog.d("queue write request");
            } else if(request instanceof  RigNotificationStateChangeRequest) {
                RigLog.d("queue notification state change request");
            }
            mOpsQueue.add(request);
        }
    }

    private synchronized void doOp(IRigDataRequest request) {
    	mIsDataOpInProgress = true;
        request.post(mBluetoothLeService);
    }

    private synchronized void nextOp() {
        if(!mOpsQueue.isEmpty() && !mIsDataOpInProgress) {
            doOp(mOpsQueue.poll());
        }
    }

    private boolean isRelevantScanRecord(byte[] rawScanRecord) {
        boolean found = false;
        List<UUID> uuidScanList = parseUUIDs(rawScanRecord);
        if (mUUIDList != null) {
            for (UUID mUUID : mUUIDList) {
                for (UUID scanUUID : uuidScanList) {
                    if (scanUUID.equals(mUUID)) {
                        found = true;  // all we need to find is one
                        break;
                    }
                }
                if (found) {
                    break;
                }
            }
        } else {
            found = true;  // if filter list is null, then take everything
        }
        return found;
    }

    private List<UUID> parseUUIDs(final byte[] advertisedData) {
        List<UUID> uuids = new ArrayList<>();

        int offset = 0;
        while (offset < (advertisedData.length - 2)) {
            int len = advertisedData[offset++];
            if (len == 0)
                break;

            int type = advertisedData[offset++];
            switch (type) {
                case 0x02: // Partial list of 16-bit UUIDs
                case 0x03: // Complete list of 16-bit UUIDs
                    while (len > 1) {
                        int uuid16 = advertisedData[offset++];
                        uuid16 += (advertisedData[offset++] << 8);
                        len -= 2;
                        uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                    }
                    break;
                case 0x06:// Partial list of 128-bit UUIDs
                case 0x07:// Complete list of 128-bit UUIDs
                    // Loop through the advertised 128-bit UUID's.
                    while (len >= 16) {
                        try {
                            // Wrap the advertised bits and order them.
                            ByteBuffer buffer = ByteBuffer.wrap(advertisedData, offset++, 16).order(ByteOrder.LITTLE_ENDIAN);
                            long mostSignificantBit = buffer.getLong();
                            long leastSignificantBit = buffer.getLong();
                            uuids.add(new UUID(leastSignificantBit, mostSignificantBit));
                        } catch (IndexOutOfBoundsException e) {
                            // Defensive programming.
                            RigLog.e(e.toString());
                        } finally {
                            // Move the offset to read the next uuid.
                            offset += 15;
                            len -= 16;
                        }
                    }
                    break;
                default:
                    offset += (len - 1);
                    break;
            }
        }

        return uuids;
    }

    private final BroadcastReceiver mBluetoothStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF: {
                        RigLog.w("Bluetooth off");
                        if(mDiscoveryObserver!=null) {
                            mDiscoveryObserver.bluetoothPowerStateChanged(false);
                        }
                        break;
                    }
                    case BluetoothAdapter.STATE_TURNING_OFF: {
                        RigLog.w("Turning Bluetooth off...");
                        break;
                    }
                    case BluetoothAdapter.STATE_ON: {
                        RigLog.w("Bluetooth on");
                        if(mDiscoveryObserver!=null) {
                            mDiscoveryObserver.bluetoothPowerStateChanged(true);
                        }
                        break;
                    }
                    case BluetoothAdapter.STATE_TURNING_ON: {
                        RigLog.w("Turning Bluetooth on...");
                        break;
                    }
                }
            }
        }
    };

    private boolean checkBluetoothState() {
        if (mBluetoothAdapter == null) {
            RigLog.d("RigCoreBluetooth.checkBluetoothState - mBluetoothAdapter == null");
            if(mDiscoveryObserver!=null) {
                mDiscoveryObserver.bluetoothDoesNotSupported();
            }
            return false;
        } else if (!mBluetoothAdapter.isEnabled()) {
            RigLog.d("RigCoreBluetooth.checkBluetoothState - mBluetoothAdapter is not enabled");
            if(mDiscoveryObserver!=null) {
                mDiscoveryObserver.bluetoothPowerStateChanged(false);
            }
            return false;
        }
        return true;
    }

    void setConnectionObserver(IRigCoreBluetoothConnectionObserver observer) {
        mConnectionObserver = observer;
    }

    void setDiscoveryObserver(IRigCoreBluetoothDiscoveryObserver observer) {
        mDiscoveryObserver = observer;
    }

    private synchronized void clearQueue () {
        if (mOpsQueue != null) {
            mIsDataOpInProgress = false;
            mOpsQueue.clear();
        }
    }

    private RigLeBaseDevice getRigLeBaseDeviceForBluetoothDevice(BluetoothDevice btDevice) {
        RigLeBaseDevice baseDevice = null;
        for(RigLeBaseDevice device : RigLeConnectionManager.getInstance().getConnectedDevices()) {
            if(device.getBluetoothDevice().getAddress().equals(btDevice.getAddress())) {
                baseDevice = device;
            }
        }
        return baseDevice;
    }

    private void cleanUpConnectionFuture() {
        // Check to see if it has already been cancelled
        if(mConnectionFuture!=null && !mConnectionFuture.isCancelled()) {
            mConnectionFuture.cancel(true);
            mConnectionFuture = null;
        }
    }

    @Override
    public void onActionGattReadRemoteRssi(BluetoothDevice bluetoothDevice, int rssi) {
        RigLog.d("__RigCoreBluetooth.onActionGattReadRemoteRssi__ : " + bluetoothDevice.getAddress() + " rssi: " + rssi);
    }

    @Override
    public void onActionGattConnected(BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattConnected__ : " + bluetoothDevice.getAddress());
        cleanUpConnectionFuture();
    }

    @Override
    public void onActionGattDisconnected(BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattDisconnected__ : " + bluetoothDevice.getAddress());
        cleanUpConnectionFuture();
        clearQueue();
        mConnectionObserver.didDisconnectDevice(bluetoothDevice);
    }

    @Override
    public void onActionGattFail(BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattFail__");
        RigLog.e("Fail: " + bluetoothDevice.getAddress());
        cleanUpConnectionFuture();
        disconnectPeripheral(bluetoothDevice);
        mConnectionObserver.didFailToConnectDevice(bluetoothDevice);
    }

    @Override
    public void onActionGattServicesDiscovered(BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattServicesDiscovered__");
        RigLog.d("Discovered: " + bluetoothDevice.getAddress());
        mConnectionObserver.didConnectDevice(bluetoothDevice);
    }

    @Override
    public void onActionGattDataAvailable(BluetoothGattCharacteristic characteristic, BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattDataAvailable__");
        mIsDataOpInProgress = false;
        RigLeBaseDevice baseDevice = getRigLeBaseDeviceForBluetoothDevice(bluetoothDevice);
        if (baseDevice != null) {
            baseDevice.didUpdateValue(bluetoothDevice, characteristic);
        }
        nextOp();
    }

    @Override
    public void onActionGattDataNotification(BluetoothGattCharacteristic characteristic, BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattDataNotification__");
        RigLeBaseDevice baseDevice = getRigLeBaseDeviceForBluetoothDevice(bluetoothDevice);
        if(baseDevice != null) {
            baseDevice.didUpdateValue(bluetoothDevice, characteristic);
        }
    }

    @Override
    public void onActionGattCharWrite(BluetoothDevice bluetoothDevice, BluetoothGattCharacteristic characteristic)
    {
        RigLog.d("__RigCoreBluetooth.onActionGattCharWrite__");
        mIsDataOpInProgress = false;
        RigLeBaseDevice baseDevice = getRigLeBaseDeviceForBluetoothDevice(bluetoothDevice);
        if(baseDevice != null) {
            baseDevice.didWriteValue(bluetoothDevice, characteristic);
        }
        nextOp();
    }

    @Override
    public void onActionGattDescriptorRead(BluetoothDevice bluetoothDevice, BluetoothGattDescriptor descriptor) {
        RigLog.d("__RigCoreBluetooth.onActionGattDescriptorRead__");
        mIsDataOpInProgress = false;
        RigLeBaseDevice baseDevice = getRigLeBaseDeviceForBluetoothDevice(bluetoothDevice);
        if(baseDevice != null) {
            baseDevice.didReadDescriptor(bluetoothDevice, descriptor);
        }
        nextOp();
    }

    @Override
    public void onActionGattDescriptorWrite(BluetoothGattDescriptor descriptor, BluetoothDevice bluetoothDevice) {
        RigLog.d("__RigCoreBluetooth.onActionGattDescriptorWrite__");
        mIsDataOpInProgress = false;
        RigLeBaseDevice baseDevice = getRigLeBaseDeviceForBluetoothDevice(bluetoothDevice);
        if (baseDevice != null) {
            baseDevice.didUpdateNotificationState(bluetoothDevice, descriptor.getCharacteristic());
        }
        nextOp();
    }
}
