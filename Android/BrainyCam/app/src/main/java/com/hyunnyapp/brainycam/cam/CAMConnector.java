package com.hyunnyapp.brainycam.cam;

import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CAMConnector {
    public final static String TAG = CAMConnector.class.getSimpleName();

    public String getInput(String strUrl) {
        String readStr = "";
        Log.i(TAG, "getInput url: " + strUrl);

        try {
            URL url = new URL(strUrl);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            if (httpURLConnection != null) {
                httpURLConnection.setDoInput(true);
                httpURLConnection.setConnectTimeout(5000);
                httpURLConnection.setRequestMethod("GET");
                httpURLConnection.setRequestProperty("accept", "*/*");
                httpURLConnection.getRequestProperty("location");
                httpURLConnection.getResponseCode();
                httpURLConnection.connect();
                InputStream inputStream = httpURLConnection.getInputStream();

                byte buffer[] = new byte[1024];

                while (inputStream.read(buffer, 0, buffer.length) > 0) {
                    readStr = readStr + new String(buffer);
                }

                httpURLConnection.disconnect();
                inputStream.close();
            }
        } catch (Exception e) {
            Log.i(TAG, "getInput str error");
            e.printStackTrace();
        }

        Log.d(TAG, readStr);
        return readStr;
    }

    public double getTheStateValue(String cmdUri, String cmd, String value) {
        double retVal = 255.1;
        String message = getInput(cmdUri);

        if (message != null) {
            String cmdStr = "<Cmd>" + cmd + "</Cmd>";
            String valueStr = "<" + value + ">";
            if ((message != null) && (message.indexOf(cmdStr) >= 0)) {
                String tempStr = message.substring(message.indexOf(cmdStr), message.length());
                if (message.indexOf(valueStr) >= 0) {
                    String tempStr2 = tempStr.substring(tempStr.indexOf(valueStr), tempStr.length());
                    if ((tempStr2.indexOf(">") >= 0) && (tempStr2.indexOf("</" + value + ">") >= 0)) {
                        String tempStr3 = tempStr2.substring(tempStr2.indexOf(">"), tempStr2.indexOf("</" + value + ">"));
                        if ((tempStr3.lastIndexOf(">") >= 0) && (1 + tempStr3.lastIndexOf(">") < tempStr3.length())) {
                            retVal = Double.valueOf(tempStr3.substring(1 + tempStr3.lastIndexOf(">"), tempStr3.length())).doubleValue();
                        }
                    }
                }
            }
        }
        return retVal;
    }

    public int getTheStateValue_int(String cmdUri, String cmd, String value) {
        int retVal = 255;

        String message = getInput(cmdUri);

        if (message != null) {
            String cmdStr = "<Cmd>" + cmd + "</Cmd>";
            String valueStr = "<" + value + ">";
            if ((message != null) && (message.indexOf(cmdStr) >= 0)) {
                String tempStr = message.substring(message.indexOf(cmdStr), message.length());
                if (message.indexOf(valueStr) >= 0) {
                    String tempStr2 = tempStr.substring(tempStr.indexOf(valueStr), tempStr.length());
                    if ((tempStr2.indexOf(">") >= 0) && (tempStr2.indexOf("</" + value + ">") >= 0)) {
                        String tempStr3 = tempStr2.substring(tempStr2.indexOf(">"), tempStr2.indexOf("</" + value + ">"));
                        if ((tempStr3.lastIndexOf(">") >= 0) && (1 + tempStr3.lastIndexOf(">") < tempStr3.length())) {
                            retVal = Integer.parseInt(tempStr3.substring(1 + tempStr3.lastIndexOf(">"), tempStr3.length()));
                        }
                    }
                }
            }
        }
        return retVal;
    }

    public boolean sendCmd(String cmdUri) {
        String message = getInput(cmdUri);
        if (message != null) {
            if (message.indexOf("<Cmd>") >= 0 && message.indexOf("</Cmd>") >= 0) {
                return true;
            }
        }
        return false;
    }

    public boolean sendCmd(String cmdUri, String cmd) {
        String message = getInput(cmdUri);
        if (message != null) {
            int i = message.indexOf("<Cmd>");
            int j = message.indexOf("</Cmd>");
            if (i >= 0 && j >= 0 && message.substring(i, j).contains(cmd)) {
                return true;
            }
        }
        return false;
    }
}