package com.hyunnyapp.brainycam.cam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.hyunnyapp.brainycam.R;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;

import java.text.DateFormat;
import java.util.Date;

public class CamConnectActivity extends Activity {
    public final static String TAG = CamConnectActivity.class.getSimpleName();

    private ImageView mImageView;
    private AnimationDrawable mFrameAnimation;

    LibVLC mLibVLC;
    private boolean mConnectRuning;
    private int mVlcPlayMode = 255;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cam_connect);

        mImageView = (ImageView) findViewById(R.id.imageView_loading_animation);
        mImageView.setBackgroundResource(R.drawable.loading_animation);
        mFrameAnimation = (AnimationDrawable) mImageView.getBackground();

        mImageView.setVisibility(View.VISIBLE);
        mFrameAnimation.setOneShot(false);
        mFrameAnimation.start();

        try {
            mLibVLC = LibVLC.getInstance();
            mLibVLC.init(this);
        } catch (LibVlcException e) {
            Toast.makeText(this,
                    "Error initializing the libVLC multimedia framework!",
                    Toast.LENGTH_LONG).show();
            finish();
        }

        sendDateTime();
    }

    @Override
        protected void onResume() {
        super.onResume();
        if (!mConnectRuning) {
            StartConnectTimer();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mFrameAnimation.stop();
        mFrameAnimation.setVisible(false, true);
        mFrameAnimation.selectDrawable(0);
        mImageView.setVisibility(View.INVISIBLE);
        mImageView.setBackgroundResource(0);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public boolean isConnectOk() {
        NetworkInfo networkinfo = ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkinfo != null && networkinfo.isConnected();
    }

    public void StartConnectTimer() {
        mConnectRuning = true;

        Thread thread = new Thread() {
            public void run() {
                if (!mConnectRuning) {
                    return;
                }
                if (!isConnectOk()) {
                    Log.d(TAG, "WIFI Service not available ");
                    return;
                }

                WifiManager wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wifi.getConnectionInfo();
                String bssID = info.getBSSID();
                CAMConnector camConnector = new CAMConnector();
                Log.d(TAG, "StartConnectTimer() bssID: " + bssID);

                if (bssID != null
                        && ((!bssID.contains("cc:d2:9b:")) && (!bssID.contains("08:d8:33:"))
                        && (!bssID.contains("8c:18:d9:")) && (!bssID.contains("CC:D2:9B:"))
                        && (!bssID.contains("08:D8:33:")) && (!bssID.contains("8C:18:D9:")))) {

                    int value = camConnector.getTheStateValue_int("http://192.168.1.254/?custom=1&cmd=8001", "8001", "Status");
                    Log.d(TAG, "getTheStateValue_int: " + value);
                    if ((value != 255) && (value != 0)) {
                        sendMessage("Please Updata FW!");
                        mConnectRuning = false;
                        try {
                            Thread.currentThread(); Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                }

                for (int i = 0; i < 3; i++) {
                    mVlcPlayMode = camConnector.getTheStateValue_int("http://192.168.1.254/?custom=1&cmd=3016", "3016", "Status");
                    if (mVlcPlayMode != 1 && mVlcPlayMode != 0 && mVlcPlayMode != 3 && mVlcPlayMode == 4) {
                        return;
                    }
                    for (int j = 0; j < 3; j++) {
                        double value = camConnector.getTheStateValue("http://192.168.1.254/?custom=1&cmd=3027", "3027", "Value");
                        if(_decode_(value) == 1) {
                            startPreview();
                            return;
                        }
                        try {
                            Thread.currentThread(); Thread.sleep(100);
                        }
                        catch (InterruptedException e) { }
                    }
                    try {
                        Thread.currentThread(); Thread.sleep(500);
                    }
                    catch (InterruptedException e) { }
                }

                sendMessage("Connect Failure!!");    // Connect Failure!
                try {
                    Thread.currentThread(); Thread.sleep(2000);
                } catch (InterruptedException e) { }
                mConnectRuning = false;
            }
        };
        thread.start();
    }

    public void sendDateTime() {
        Thread thread = new Thread() {
            public void run() {
                Time currentTime = new Time();
                currentTime.setToNow();

                int year = currentTime.year;
                int month = currentTime.month + 1;
                int day = currentTime.monthDay;
                int hour = currentTime.hour;
                int minute = currentTime.minute;
                int second = currentTime.second;

                String strYear = String.valueOf(year);
                String strMonth;
                String strDay;
                String strHour;
                String strMinute;
                String strSecond;

                if (month < 10) {
                    strMonth = (new StringBuilder("0")).append(String.valueOf(month)).toString();
                } else {
                    strMonth = String.valueOf(month);
                }
                if (day < 10) {
                    strDay = (new StringBuilder("0")).append(String.valueOf(day)).toString();
                } else {
                    strDay = String.valueOf(day);
                }
                if (hour < 10) {
                    strHour = (new StringBuilder("0")).append(String.valueOf(hour)).toString();
                } else {
                    strHour = String.valueOf(hour);
                }
                if (minute < 10) {
                    strMinute = (new StringBuilder("0")).append(String.valueOf(minute)).toString();
                } else {
                    strMinute = String.valueOf(minute);
                }
                if (second < 10) {
                    strSecond = (new StringBuilder("0")).append(String.valueOf(second)).toString();
                } else {
                    strSecond = String.valueOf(second);
                }

                String strDate = (new StringBuilder(strYear)).append("-").append(strMonth).append("-").append(strDay).toString();
                String strTime = (new StringBuilder(strHour)).append(":").append(strMinute).append(":").append(strSecond).toString();

                CAMConnector commander = new CAMConnector();
                commander.sendCmd((new StringBuilder("http://192.168.1.254/?custom=1&cmd=3005&str=")).append(strDate).toString(), "3005");
                commander.sendCmd((new StringBuilder("http://192.168.1.254/?custom=1&cmd=3006&str=")).append(strTime).toString(), "3006");
            }
        };
        thread.start();

    }

    public void startPreview() {
        Log.d(TAG, "start preview");
        Intent intent = new Intent(this, CamPreviewActivity.class);
        intent.putExtra(CamPreviewActivity.LOCATION, "rtsp://192.168.1.254/sjcam.mov");
        startActivity(intent);
        finish();
    }

    public void sendMessage(String message) {
        String currentDateTimeString = DateFormat.getTimeInstance().format(new Date());
        Log.d(TAG, "[" + currentDateTimeString + "]" + message);
    }

    public int _decode_(double d) {
        Log.i(TAG, "_decode_:" + d);

        int i = (int) (d % 10000);
        int j = (int) (d / 10000);
        int k = i / 1000;
        int l = (i % 1000) / 100;

        return j != (((i % 100) / 10 + 9) % 10) * 1000 + ((k + 9) % 10) * 100 + ((i % 10 + 8) % 10) * 10 + (l + 8) % 10 ? 0 : 1;
    }
}
