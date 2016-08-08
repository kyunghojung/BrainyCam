package com.hyunnyapp.brainycam.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.UUID;

public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private ArrayList<BluetoothGatt> mBluetoothGatts = null;

    public final static String ACTION_GATT_CONNECTED =
            "com.ents.smarthome.ble.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.ents.smarthome.ble.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.ents.smarthome.ble.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.ents.smarthome.ble.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.ents.smarthome.ble.EXTRA_DATA";
    public final static String DEVICE_DOES_NOT_SUPPORT_UART =
            "com.ents.smarthome.ble.DEVICE_DOES_NOT_SUPPORT_UART";
    public final static String EXTRA_ADDRESS =
            "com.ents.smarthome.multible.EXTRA_ADDRESS";

    public static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public static final UUID RX_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID RX_CHAR_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");
    public static final UUID TX_CHAR_UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");

    public BluetoothGattCharacteristic mNotifyCharacteristic;
    
    
    public void WriteValue(String address, String strData) {
        Log.d(TAG, "WriteValue() address: "+address+", WriteValue: "+strData);
        WriteValue(address, strData.getBytes());
    }

    public void WriteValue(String address, byte[] data)
    {
        if (mBluetoothGatts != null) {
            Log.d(TAG, "WriteValue() mBluetoothGatts != null size: "+mBluetoothGatts.size());
            for (BluetoothGatt tempGatt : mBluetoothGatts) {
                Log.d(TAG, "tempGatt address: "+tempGatt.getDevice().getAddress());
                if(tempGatt.getDevice().getAddress().equals(address)) {
                    BluetoothGattService RxService = tempGatt.getService(RX_SERVICE_UUID);

                    if (RxService == null) {
                        broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                        return;
                    }
                    BluetoothGattCharacteristic RxChar = RxService.getCharacteristic(RX_CHAR_UUID);
                    if (RxChar == null) {
                        broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
                        return;
                    }
                    RxChar.setValue(data);
                    boolean status = tempGatt.writeCharacteristic(RxChar);

                    Log.d(TAG, "write TXchar - status=" + status);
                }
            }
        }
        else {

            Log.d(TAG, "WriteValue() mBluetoothGatts == null ");
        }
    }

    public void enableTXNotification(BluetoothGatt gatt)
    {
        Log.i(TAG, "enableTXNotification() "+gatt.getDevice().getName());

        BluetoothGattService RxService = gatt.getService(RX_SERVICE_UUID);
        if (RxService == null) {
            Log.i(TAG, "Rx service not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        BluetoothGattCharacteristic TxChar = RxService.getCharacteristic(TX_CHAR_UUID);
        if (TxChar == null) {
            Log.i(TAG, "Tx charateristic not found!");
            broadcastUpdate(DEVICE_DOES_NOT_SUPPORT_UART);
            return;
        }
        gatt.setCharacteristicNotification(TxChar,true);

        BluetoothGattDescriptor descriptor = TxChar.getDescriptor(CCCD);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        gatt.writeDescriptor(descriptor);

        broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, gatt.getDevice().getAddress());

    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.d(TAG, "onConnectionStateChange() " + gatt.getDevice().getName()
                    + ", oldStatus=" + status + " NewStates=" + newState);
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    intentAction = ACTION_GATT_CONNECTED;

                    broadcastUpdate(intentAction, gatt.getDevice().getAddress());

                    if(!mBluetoothGatts.contains(gatt)) {
                        Log.d(TAG, gatt.getDevice().getName()+"isn't contain mBluetoothGatts!!!");
                        mBluetoothGatts.add(gatt);
                    }

                    Log.i(TAG, "Connected to GATT server.");
                    // Attempts to discover services after successful connection.
                    Log.i(TAG, "Attempting to start service discovery:" + gatt.discoverServices());
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    intentAction = ACTION_GATT_DISCONNECTED;

                    Log.i(TAG, "Disconnected from GATT server.");
                    broadcastUpdate(intentAction, gatt.getDevice().getAddress());
                }
        	}
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	Log.w(TAG, "onServicesDiscovered received: " + status);
                enableTXNotification(gatt);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.e(TAG, "onCharacteristicRead");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getAddress());
                Log.i(TAG, "onCharacteristicReadSUCCESS");
            }
            else {
                Log.e(TAG, "onCharacteristicRead FAILED");
            }
            
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, gatt.getDevice().getAddress());
            Log.e(TAG, "onCharacteristicChanged");
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
        								int status)
        {
        	if (status == BluetoothGatt.GATT_SUCCESS) {
        		Log.d(TAG, "OnCharacteristicWrite SUCCESS");
			}
        	else{
        		Log.e(TAG, "OnCharacteristicWrite FAILED");
        	}
        }
        
        @Override
        public void onDescriptorRead(BluetoothGatt gatt,
                                         BluetoothGattDescriptor bd,
                                         int status) {
        	Log.e(TAG, "onDescriptorRead");
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
        								 BluetoothGattDescriptor bd,
                                         int status) {
        	Log.e(TAG, "onDescriptorWrite");
        }
        
        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int a, int b)
        {
        	Log.e(TAG, "onReadRemoteRssi");
        }
        
        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int a)
        {
        	Log.e(TAG, "onReliableWriteCompleted");
        }
        
    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final String address) {
        final Intent intent = new Intent(action);
        intent.putExtra(EXTRA_ADDRESS, address);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic,
                                 final String address) {
        final Intent intent = new Intent(action);
        final byte[] data = characteristic.getValue();

        if (TX_CHAR_UUID.equals(characteristic.getUuid())) {
            try {
                String text = new String(characteristic.getValue(), "UTF-8");
                Log.d(TAG, String.format("Received TX: ", text));
                intent.putExtra(EXTRA_DATA, text);
            }
            catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }
        else {
            intent.putExtra(EXTRA_DATA, "null");
        }
/*
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data) {
                stringBuilder.append(String.format("%02X", byteChar));
            }
            intent.putExtra(EXTRA_DATA, stringBuilder.toString());
        } else {
            intent.putExtra(EXTRA_DATA, "null");
        }
*/
        intent.putExtra(EXTRA_ADDRESS, address);

        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        mBluetoothGatts = new ArrayList<BluetoothGatt>();
        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
/*
        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }
*/
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }

        for (BluetoothGatt tempGatt : mBluetoothGatts) {
            if (tempGatt.getDevice().getAddress().equals(address)) {

                tempGatt.disconnect();
                tempGatt.close();
                Log.w(TAG, "mBluetoothGatts.remove(): "+tempGatt.getDevice().getName());
                mBluetoothGatts.remove(tempGatt);
            }
        }

        BluetoothGatt gatt = device.connectGatt(this, true, mGattCallback);
        Log.w(TAG, "mBluetoothGatts.add(): "+gatt.getDevice().getName());
        mBluetoothGatts.add(gatt);

        Log.w(TAG, "mBluetoothGatts.size: " + mBluetoothGatts.size());
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect(String address) {
        Log.e(TAG, "disconnect() address: "+address);

        BluetoothGatt gatt = null;
        for (BluetoothGatt tempGatt : mBluetoothGatts) {
            if (tempGatt.getDevice().getAddress().equals(address)) {
                gatt = tempGatt;
            }
        }

        if (mBluetoothAdapter == null || gatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        int connectionState = mBluetoothManager.getConnectionState(device, BluetoothProfile.GATT);

        if (gatt != null) {
            if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
                gatt.disconnect();
                gatt.close();
            }
            Log.w(TAG, "mBluetoothGatts.remove(): "+gatt.getDevice().getName());
            mBluetoothGatts.remove(gatt);
        }
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {

        if(mBluetoothGatts == null || mBluetoothGatts.size() == 0) {
            Log.e(TAG, "close() Already closed. ");
            return;
        }
        Log.e(TAG, "close() mBluetoothGatts size: "+mBluetoothGatts.size());

        for (BluetoothGatt tempGatt : mBluetoothGatts) {
            Log.e(TAG, "close() gatt: "+tempGatt.getDevice().getName());
            tempGatt.disconnect();
            tempGatt.close();
            Log.w(TAG, "mBluetoothGatts.remove(): " + tempGatt.getDevice().getName());
            mBluetoothGatts.remove(tempGatt);
        }
        mBluetoothGatts = null;
        mNotifyCharacteristic = null;
    }

}
