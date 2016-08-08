package com.hyunnyapp.brainycam.ble;


import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

public class BleConnectionManager  {
	private static final String TAG = BleConnectionManager.class.getSimpleName();

	private ArrayList<BluetoothDevice> mBleDevices = new ArrayList<BluetoothDevice>();
	private Context mContext;

	private BleConnectionManagerDelegate delegate;
	private BluetoothLeService mBluetoothLeService;

	 // Code to manage Service lifecycle.
    private  ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                mBluetoothLeService = null;
            }
            else{
                Log.e(TAG, "mBluetoothLeService is okay");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        	Log.e(TAG, "onServiceDisconnected");
           // mBluetoothLeService = null;
        }
    };
    
    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			String address = intent.getStringExtra(BluetoothLeService.EXTRA_ADDRESS);
			Log.i(TAG, "Action: "+action+", Address : " + address);

			BluetoothDevice device = null;
			for (BluetoothDevice tempDevice : mBleDevices) {
				if (tempDevice.getAddress().equals(address)) {
					device = tempDevice;
					Log.e(TAG, "device() "+device.getName());
					break;
				}
			}

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
            	Log.e(TAG, "ACTION_GATT_CONNECTED");
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.e(TAG,  "ACTION_GATT_DISCONNECTED");

				disconnect(device);

                if (delegate != null) {
					delegate.disconnected(BleConnectionManager.this, device);
				}
            }
            else if(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
				Log.e(TAG, "ACTION_GATT_SERVICES_DISCOVERED");
        		if (delegate != null) {
        			delegate.connected(BleConnectionManager.this, device);
        		}
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
				Log.e(TAG, "ACTION_DATA_AVAILABLE");
            	String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
            	
            	if (delegate != null) {
					delegate.receivedData(BleConnectionManager.this, device, data);
				}
            }
			else if (action.equals(BluetoothLeService.DEVICE_DOES_NOT_SUPPORT_UART)){
				Log.e(TAG, "DEVICE_DOES_NOT_SUPPORT_UART");
				Toast.makeText(BleConnectionManager.this.mContext, "Fail!", Toast.LENGTH_SHORT).show();
			} else{
            	Toast.makeText(BleConnectionManager.this.mContext, "Unkonwn!", Toast.LENGTH_SHORT).show();
            }
        }
    };
	
	
	public BleConnectionManager(Context context){
		super();
		this.mContext = context;
		
		Intent gattServiceIntent = new Intent(this.mContext, BluetoothLeService.class);
		this.mContext.bindService(gattServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);
		Log.d(TAG, "Try to bindService=");

		this.mContext.registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
	}

	public void connect(BluetoothDevice device){
		Log.d(TAG, "try connect device: " + device.getName());

		for (BluetoothDevice tempDevice : mBleDevices) {
			if (tempDevice.getAddress().equals(device.getAddress())) {
				return;
			}
		}

		if (mBluetoothLeService != null) {
			mBluetoothLeService.connect(device.getAddress());
		}
		mBleDevices.add(device);
	}


	public void disconnect(BluetoothDevice device){
        Log.e(TAG, "disconnect() device: " + device.getName());
		if (mBluetoothLeService != null) {
			mBluetoothLeService.disconnect(device.getAddress());
		} 
		return;
	}

	public void close() {
		Log.e(TAG, "release resource");

		mBleDevices.clear();

		if (mGattUpdateReceiver != null) {
			this.mContext.unregisterReceiver(mGattUpdateReceiver);
			mGattUpdateReceiver = null;
		}
		
		if (mServiceConnection != null) {
			this.mContext.unbindService(mServiceConnection);
			mServiceConnection = null;
		}
        
		if(mBluetoothLeService != null){
			mBluetoothLeService.close();
		   	mBluetoothLeService = null;
		}
		
        Log.d(TAG, "releaseSource");
	}
	

	public void sendData(BluetoothDevice device, String data) {
		if (mBluetoothLeService != null && isConnected(device)) {
			mBluetoothLeService.WriteValue(device.getAddress(), data);
		}
	}
	
	
	public void sendData(BluetoothDevice device, byte[] data) {
		if (mBluetoothLeService != null && isConnected(device)) {
			mBluetoothLeService.WriteValue(device.getAddress(), data);
		}
	}
	
	
	public boolean isConnected(BluetoothDevice device){
		for (BluetoothDevice tempDevice : mBleDevices) {
			if (tempDevice.getAddress().equals(device.getAddress())) {
				return true;
			}
		}
		return false;
	}

	public boolean isConnected(String device){
		for (BluetoothDevice tempDevice : mBleDevices) {
			if (tempDevice.getName().equals(device)) {
				return true;
			}
		}
		return false;
	}

	public BleConnectionManagerDelegate getDelegate() {
		return delegate;
	}

	
	public void setDelegate(BleConnectionManagerDelegate delegate) {
		this.delegate = delegate;
	}

	public void switchContext(Context context) {
		this.mContext = context;
	}

	public ArrayList<BluetoothDevice> getDeviceList() {
		return mBleDevices;
	}


	private  IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
		intentFilter.addAction(BluetoothLeService.DEVICE_DOES_NOT_SUPPORT_UART);
        return intentFilter;
    }
}
