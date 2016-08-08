package com.hyunnyapp.brainycam.cam;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import com.hyunnyapp.brainycam.R;
import com.hyunnyapp.brainycam.ble.BleConnectionManager;
import com.hyunnyapp.brainycam.ble.BleConnectionManagerDelegate;
import com.hyunnyapp.brainycam.ble.BleMultiConnector;
import com.hyunnyapp.brainycam.joystick.JoystickMovedListener;
import com.hyunnyapp.brainycam.joystick.JoystickView;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaList;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class CamPreviewActivity extends Activity implements SurfaceHolder.Callback,
        IVideoPlayer, BleConnectionManagerDelegate {
    public final static String TAG = CamPreviewActivity.class.getSimpleName();

    public final static String LOCATION = "com.ents.brainyhouse.cam.location";

    public final static String DEVICE_NAME = "BrainyC";
    public final static String COMMAND_CONNECT_DEVICE = "com.ents.smarthome.COMMAND_CONNECT_DEVICE";
    public final static String COMMAND_CONNECTED_DEVICE = "com.ents.smarthome.COMMAND_CONNECTED_DEVICE";
    public final static String COMMAND_DISCONNECT_DEVICE = "com.ents.smarthome.COMMAND_DISCONNECT_DEVICE";
    public final static String COMMAND_DISCONNECTED_DEVICE = "com.ents.smarthome.COMMAND_DISCONNECTED_DEVICE";
    public final static String COMMAND_RECEIVE_DATA = "com.ents.smarthome.COMMAND_RECEIVE_DATA";
    public final static String COMMAND_SEND_DATA = "com.ents.smarthome.COMMAND_SEND_DATA";

    BleConnectionManager mBleConnectionManager = null;
    private static BlockingQueue<ArrayList<Object>> mCommendQueue;
    private ArrayList<Object> mCommendArrayList;
    private ArrayList<BluetoothDevice> mDeviceList = new ArrayList<BluetoothDevice>();
    private boolean isConnectingDevice = false;
    private BluetoothDevice mWaitToConnectDevice = null;
    private BluetoothDevice mConnectingDevice = null;


    private String mFilePath;

    // display surface
    private SurfaceView mSurface;
    private SurfaceHolder holder;

    // media player
    private LibVLC libvlc;
    private int mVideoWidth;
    private int mVideoHeight;
    private final static int VideoSizeChanged = -1;

    private JoystickView mJoystickView;

    /*************
     * Activity
     *************/

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Receive path to play from intent
        Intent intent = getIntent();
        mFilePath = intent.getExtras().getString(LOCATION);

        Log.d(TAG, "Playing back " + mFilePath);

        mSurface = (SurfaceView) findViewById(R.id.surface);
        holder = mSurface.getHolder();
        holder.addCallback(this);

        mJoystickView = (JoystickView)findViewById(R.id.joystickView_controler);
        mJoystickView.setOnJostickMovedListener(mJoystickMovedListener);

        mBleConnectionManager = BleMultiConnector.getInstance().getBleConnectionManager();

        Log.d(TAG, "Playing back " + mFilePath);

        if(mBleConnectionManager.isConnected(DEVICE_NAME)) {
            mBleConnectionManager.switchContext(this);
            mBleConnectionManager.setDelegate(this);
        }
        mDeviceList = mBleConnectionManager.getDeviceList();

        mCommendQueue = new ArrayBlockingQueue<>(1024);
        mCommendThread.start();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setSize(mVideoWidth, mVideoHeight);
    }

    @Override
    protected void onResume() {
        super.onResume();
        createPlayer(mFilePath);
    }

    @Override
    protected void onPause() {
        super.onPause();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    private JoystickMovedListener mJoystickMovedListener = new JoystickMovedListener()
    {
        @Override
        public void OnMoved(int pan, int tilt)
        {
            Move(pan, tilt);
        }

        public void Move(int pan, int tilt)
        {
            // limit to {0..10}
            int radius = (byte) ( Math.min( Math.sqrt((pan*pan) + (tilt*tilt)), 10.0 ) );
            // scale to {0..35}
            int angle = (byte) ( Math.atan2(-pan, -tilt) * 18.0 / Math.PI + 36.0 + 0.5 );

            if( angle >= 36 )
                angle = (byte)(angle-36);

            if((angle >= 0 && angle < 5) || (angle >= 32 && angle < 36)) {
                Log.d(TAG, "Move Up ");
                sendData("u");
            }
            else if(angle >= 5 && angle < 14) {
                Log.d(TAG, "Move Left ");
                sendData("l");
            }
            else if(angle >= 14 && angle < 23) {
                Log.d(TAG, "Move Down ");
                sendData("d");
            }
            else if(angle >= 23 && angle < 32) {
                Log.d(TAG, "Move Right ");
                sendData("r");
            }
        }

        @Override
        public void OnReleased()
        {

        }

        @Override
        public void OnReturnedToCenter()
        {
            Log.d(TAG, "Move Center ");
            sendData("c");
        };
    };

    /*************
     * Surface
     *************/

    public void surfaceCreated(SurfaceHolder holder) {
        Log.d(TAG, "surfaceCreated");
    }

    public void surfaceChanged(SurfaceHolder surfaceholder, int format,
                               int width, int height) {
        Log.d(TAG, "surfaceChanged");
        if (libvlc != null)
            libvlc.attachSurface(holder.getSurface(), this);
    }

    public void surfaceDestroyed(SurfaceHolder surfaceholder) {
        Log.d(TAG, "surfaceDestroyed");
    }

    private void setSize(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        if (mVideoWidth * mVideoHeight <= 1)
            return;

        // get screen size
        int w = getWindow().getDecorView().getWidth();
        int h = getWindow().getDecorView().getHeight();

        // getWindow().getDecorView() doesn't always take orientation into
        // account, we have to correct the values
        boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        if (w > h && isPortrait || w < h && !isPortrait) {
            int i = w;
            w = h;
            h = i;
        }

        float videoAR = (float) mVideoWidth / (float) mVideoHeight;
        float screenAR = (float) w / (float) h;

        if (screenAR < videoAR)
            h = (int) (w / videoAR);
        else
            w = (int) (h * videoAR);

        // force surface buffer size
        holder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = mSurface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width,
                               int visible_height, int sar_num, int sar_den) {
        Message msg = Message.obtain(mHandler, VideoSizeChanged, width, height);
        msg.sendToTarget();
    }

    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            if (media.length() > 0) {
                Toast toast = Toast.makeText(this, media, Toast.LENGTH_LONG);
                toast.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0,
                        0);
                toast.show();
            }

            // Create a new media player
            libvlc = LibVLC.getInstance();
            libvlc.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            libvlc.setSubtitlesEncoding("");
            libvlc.setAout(LibVLC.AOUT_OPENSLES);
            libvlc.setTimeStretching(true);
            libvlc.setChroma("RV32");
            libvlc.setVerboseMode(true);
            LibVLC.restart(this);
            EventHandler.getInstance().addHandler(mHandler);
            holder.setFormat(PixelFormat.RGBX_8888);
            holder.setKeepScreenOn(true);
            MediaList list = libvlc.getMediaList();
            list.clear();
            list.add(new Media(libvlc, LibVLC.PathToURI(media)), false);
            libvlc.playIndex(0);
        } catch (Exception e) {
            Toast.makeText(this, "Error creating player!", Toast.LENGTH_LONG).show();
        }
    }

    private void releasePlayer() {
        if (libvlc == null)
            return;
        EventHandler.getInstance().removeHandler(mHandler);
        libvlc.stop();
        libvlc.detachSurface();
        holder = null;
        libvlc.closeAout();
        libvlc.destroy();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
    }

    /*************
     * Events
     *************/

    private Handler mHandler = new MyHandler(this);

    private static class MyHandler extends Handler {
        private WeakReference<CamPreviewActivity> mOwner;

        public MyHandler(CamPreviewActivity owner) {
            mOwner = new WeakReference<CamPreviewActivity>(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            CamPreviewActivity player = mOwner.get();

            // SamplePlayer events
            if (msg.what == VideoSizeChanged) {
                player.setSize(msg.arg1, msg.arg2);
                return;
            }

            // Libvlc events
            Bundle b = msg.getData();
            switch (b.getInt("event")) {
                case EventHandler.MediaPlayerEndReached:
                    player.releasePlayer();
                    break;
                case EventHandler.MediaPlayerPlaying:
                case EventHandler.MediaPlayerPaused:
                case EventHandler.MediaPlayerStopped:
                default:
                    break;
            }
        }
    }

    // Bluetooth
    @Override
    public void connected(BleConnectionManager manager, BluetoothDevice device) {
        Log.d(TAG, "Device Connected " + device.getName());
        Log.d(TAG, "isConnectingDevice " + isConnectingDevice);
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
        Log.d(TAG, "receivedData: " + device.getName() + ", data: " + data);
        addCommand(COMMAND_RECEIVE_DATA, device, data);
    }

    public void connectDevices(ArrayList<BluetoothDevice> deviceList) {
        Log.d(TAG, "connectDevices: " + deviceList.size());
        for(BluetoothDevice device:deviceList) {
            Log.d(TAG,"COMMAND_CONNECT_DEVICE "+device.getName());
            addCommand(COMMAND_CONNECT_DEVICE, device, null);
        }
    }

    private void sendData(String data) {
        Log.d(TAG, "sendData: " + data);
        Log.d(TAG, "mDeviceList.size(): " + mDeviceList.size());
        for (BluetoothDevice device : mDeviceList) {
            if (device.getName().equals(DEVICE_NAME)) {
                Log.d(TAG, "addCommand: COMMAND_SEND_DATA" + data);
                addCommand(COMMAND_SEND_DATA, device, data);
                break;
            }
        }
    }

    public static void addCommand(String command, Object object1, Object object2){
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
                    cmmandProcess(mCommendArrayList);
                }
            }
        }
    };
    private void cmmandProcess(ArrayList<Object> receivedArrayList) {
        String commend = (String) receivedArrayList.get(0);
        BluetoothDevice device = (BluetoothDevice) receivedArrayList.get(1);
        String data;

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
                data = (String) receivedArrayList.get(2);
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

    private void processMessage(BluetoothDevice device, String message) {
        switch(device.getName()) {
            case DEVICE_NAME:
/*
                if(message.equals(COMMAND_CONNECTED_DEVICE) || message.equals(COMMAND_DISCONNECTED_DEVICE)) {
                    sendMessage(FRAGMENT_LIVINGROOM, message);
                    sendMessage(FRAGMENT_SECURITY, message);
                }
                else{
                    String datas[] = message.split(";");
                    if(datas[0].equals("L")) {
                        for (int i=1; i < datas.length; i++) {
                            sendMessage(FRAGMENT_LIVINGROOM,datas[i]);
                        }
                    }
                    else if(datas[0].equals("S")) {
                        for (int i=1; i < datas.length; i++) {
                            sendMessage(FRAGMENT_SECURITY,datas[i]);
                        }
                    }
                }
                else{

                }
*/
                break;
            default:
                break;
        }
    }
}
