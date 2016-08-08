package com.hyunnyapp.brainycam;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.hyunnyapp.brainycam.ble.BleConnectionManager;
import com.hyunnyapp.brainycam.ble.BleConnectionManagerDelegate;
import com.hyunnyapp.brainycam.ble.BleMultiConnector;
import com.hyunnyapp.brainycam.cam.CamConnectActivity;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class MainActivity extends Activity implements BleConnectionManagerDelegate {
    public static final String TAG = MainActivity.class.getSimpleName();;

    private ListView mMessageListView;
    private ArrayAdapter<String> mListAdapter;
    private Button mButtonStrat;
    private Button mButtonConnect;
    private Button mButtonLed;
    private boolean mIsLEDStatus = false;

    public final static String DEVICE_NAME = "BrainyC";

    public final static String COMMAND_CONNECT_DEVICE = "com.ents.smarthome.COMMAND_CONNECT_DEVICE";
    public final static String COMMAND_CONNECTED_DEVICE = "com.ents.smarthome.COMMAND_CONNECTED_DEVICE";
    public final static String COMMAND_DISCONNECT_DEVICE = "com.ents.smarthome.COMMAND_DISCONNECT_DEVICE";
    public final static String COMMAND_DISCONNECTED_DEVICE = "com.ents.smarthome.COMMAND_DISCONNECTED_DEVICE";
    public final static String COMMAND_RECEIVE_DATA = "com.ents.smarthome.COMMAND_RECEIVE_DATA";
    public final static String COMMAND_SEND_DATA = "com.ents.smarthome.COMMAND_SEND_DATA";

    private static final int REQUEST_ENABLE_BT = 1;

    private static final int UART_PROFILE_CONNECTED = 20;
    private static final int UART_PROFILE_DISCONNECTED = 21;

    private static final long SCAN_PERIOD = 10000; //10 seconds
    private BluetoothAdapter mBluetoothAdapter = null;
    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<BluetoothDevice>();
    private BluetoothDevice mConnectingDevice = null;
    private BluetoothDevice mWaitToConnectDevice = null;
    private HashMap<String, Integer> devRssiValues = new HashMap<String, Integer>();;
    private int mState = UART_PROFILE_DISCONNECTED;
    private boolean isConnectingDevice = false;

    private BleConnectionManager mBleConnectionManager = null;
    private static BlockingQueue<ArrayList<Object>> mCommendQueue;
    private ArrayList<Object> mCommendArrayList;

    private Handler mHandler = new Handler();
    private Handler mScanHandler = new Handler();

    /************************************
     *  Bluetooth
     *************************************/
    private boolean initBle() {
        if (mBluetoothAdapter == null) {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
                return false;
            }

            BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();

            if (mBluetoothAdapter == null) {
                Toast.makeText(this, R.string.bluetooth_not_supported, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return true;
    }

    private void startScanLeDevice(final boolean enable) {
        Log.d(TAG, "scanLeDevice() " + enable);

        this.setProgressBarIndeterminateVisibility(true);
        if (enable) {
            mScanHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    stopScanAndConnectDevice();
                }
            }, SCAN_PERIOD);

            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private void stopScanAndConnectDevice() {
        this.setProgressBarIndeterminateVisibility(false);
        mBluetoothAdapter.stopLeScan(mLeScanCallback);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectDevices(mDeviceList);
            }
        }, 2000);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addDevice(device, rssi);
                }
            });
        }
    };

    private void addDevice(BluetoothDevice device, int rssi) {
        boolean deviceFound = false;

        for (BluetoothDevice listDev : mDeviceList) {
            if (listDev.getAddress().equals(device.getAddress())) {
                deviceFound = true;
                break;
            }
        }

        if (!deviceFound) {
            devRssiValues.put(device.getAddress(), rssi);
            Log.d(TAG, "Device Found! " + device.getName() + ", rssi: " + rssi);
            mDeviceList.add(device);
        }
    }


    Thread mCommendThread = new Thread() {
        public void run() {
            while(true) {
                try {
                    Log.d(TAG, "mCommendThread Started!!! ");
                    mCommendArrayList = mCommendQueue.take();
                    Log.d(TAG,"take commend commend: "+mCommendArrayList.get(0));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    eventProcess(mCommendArrayList);
                }
            }
        }
    };

    private void eventProcess(ArrayList<Object> receivedArrayList) {
        String commend = (String) receivedArrayList.get(0);
        BluetoothDevice device = (BluetoothDevice) receivedArrayList.get(1);

        Log.d(TAG,"eventProcess: "+commend+", device: "+device.getName());
        switch (commend) {
            case COMMAND_CONNECT_DEVICE:
                Log.d(TAG, "COMMAND_CONNECT_DEVICE: device: " + device.getName());
                if(isConnectingDevice == true) {
                    Log.d(TAG, "isConnectingDevice add again " + device.getName());
                    mWaitToConnectDevice = device;
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            addCommand(COMMAND_CONNECT_DEVICE, mWaitToConnectDevice, null);
                            mWaitToConnectDevice = null;
                        }
                    }, 5000);
                }
                else {
                    isConnectingDevice = true;
                    mConnectingDevice = device;
                    mBleConnectionManager.connect(mConnectingDevice);
                }
                break;
            case COMMAND_DISCONNECT_DEVICE:
                Log.d(TAG, "COMMAND_DISCONNECT_DEVICE ");
                break;
            case COMMAND_RECEIVE_DATA:
                Log.d(TAG, "COMMAND_RECEIVE_DATA ");
                String data = (String) receivedArrayList.get(2);
                Log.d(TAG, "receive data: " + data);

                processMessage(device, data);
                break;
            case COMMAND_SEND_DATA:
                Log.d(TAG, "COMMAND_SEND_DATA ");
                data = (String) receivedArrayList.get(2);
                Log.d(TAG, "send data: " + data);

                mBleConnectionManager.sendData(device, data);
                break;

            default:
                break;
        }
    }

    public static void addCommand(String command, Object object1, Object object2){
        Log.d(TAG, "addCommand " + command);
        ArrayList<Object> commandArrayList = new ArrayList<Object>();
        commandArrayList.add(command);
        if(object1 != null) {
            commandArrayList.add(object1);
        }
        if(object2 != null) {
            commandArrayList.add(object2);
        }

        try {
            mCommendQueue.put(commandArrayList);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void connected(BleConnectionManager manager, BluetoothDevice device) {
        Log.d(TAG, "Device Connected " + device.getName());
        if(isConnectingDevice == true && mConnectingDevice.getAddress().equals(device.getAddress())) {
            mConnectingDevice = null;
            isConnectingDevice = false;
        }

        processMessage(device, COMMAND_CONNECTED_DEVICE);
    }

    @Override
    public void disconnected(BleConnectionManager manager, BluetoothDevice device) {
        Log.d(TAG, "Device Disonnected " + device.getName());
        processMessage(device, COMMAND_DISCONNECTED_DEVICE);
    }

    @Override
    public void failToConnect(BleConnectionManager manager, BluetoothDevice device) {
        Log.d(TAG, "Device Fail To Connect!  " + device.getName());

    }

    @Override
    public void receivedData(BleConnectionManager manager, BluetoothDevice device, String data) {
        addCommand(COMMAND_RECEIVE_DATA, device, data);
    }

    public void connectDevices(ArrayList<BluetoothDevice> deviceList) {
        Log.d(TAG, "connectDevices: " + deviceList.size());
        for(BluetoothDevice device:deviceList) {
            Log.d(TAG,"COMMAND_CONNECT_DEVICE "+device.getName());
            addCommand(COMMAND_CONNECT_DEVICE, device, null);
        }
    }

    private void processMessage(BluetoothDevice device, String message) {
        switch(device.getName()) {
            case DEVICE_NAME:
                if(message.equals(COMMAND_CONNECTED_DEVICE)) {
                    showMessage(message);
                    sendData("ledOn");
                }
                else if(message.equals(COMMAND_DISCONNECTED_DEVICE)) {
                    showMessage(message);
                    sendData("ledOff");
                }
                else{
                    showMessage(message);
                }
                break;
            default:
                break;
        }
    }

    private void sendData(String data) {
        for (BluetoothDevice device : mDeviceList) {
            if (device.getName().equals(DEVICE_NAME)) {
                addCommand(COMMAND_SEND_DATA, device, data);
                break;
            }
        }
    }
    /************************************
     *  Activity Life Cycle
     *************************************/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mMessageListView = (ListView) findViewById(R.id.listMessage);
        mListAdapter = new ArrayAdapter<String>(this, R.layout.message_detail);
        mMessageListView.setAdapter(mListAdapter);
        mMessageListView.setDivider(null);

        mButtonStrat = (Button)findViewById(R.id.buttonStart);
        mButtonStrat.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, CamConnectActivity.class);
                startActivity(intent);
            }
        });

        mButtonConnect = (Button)findViewById(R.id.buttonConnect);
        mButtonConnect.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    startScanLeDevice(true);
                }
            }
        });
        mButtonLed = (Button)findViewById(R.id.buttonLED);
        mButtonLed.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(mIsLEDStatus) {
                    mButtonLed.setText("OFF");
                    mIsLEDStatus = false;
                    sendData("ledOff");
                }
                else {
                    mButtonLed.setText("ON");
                    mIsLEDStatus = true;
                    sendData("ledOn");
                }
            }
        });

        // Bluetooth init
        initBle();
        mBleConnectionManager = new BleConnectionManager(this);
        BleMultiConnector.getInstance().setBleConnectionManager(mBleConnectionManager);
        mBleConnectionManager.setDelegate(this);

        mScanHandler = new Handler();

        mCommendQueue = new ArrayBlockingQueue<>(1024);
        mCommendThread.start();
    }
    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBleConnectionManager.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater=getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                // Bluetooth On
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    startScanLeDevice(true);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /************************************
     *  Log View
     *************************************/
    private void showMessage(final String message) {
        runOnUiThread(new Runnable() {
            public void run() {
                try {
                    String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
                    mListAdapter.add("[" + currentDateTimeString + "] " + message);
                    mMessageListView.smoothScrollToPosition(mListAdapter.getCount() - 1);

                } catch (Exception e) {
                    Log.e(TAG, e.toString());
                }
            }
        });
    }
}
