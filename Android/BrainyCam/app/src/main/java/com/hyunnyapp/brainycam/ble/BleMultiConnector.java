package com.hyunnyapp.brainycam.ble;

public class BleMultiConnector {

    private static BleMultiConnector instance;
    private BleConnectionManager mBleConnectionManager;

    public BleConnectionManager getBleConnectionManager() {
        return mBleConnectionManager;
    }

    public void setBleConnectionManager(BleConnectionManager bleConnectionManager) {
        this.mBleConnectionManager = bleConnectionManager;
    }

    private BleMultiConnector(){
        super();
    }

    public static BleMultiConnector getInstance(){
        if (instance == null) {
            instance = new BleMultiConnector();
        }

        return instance;
    }

}
