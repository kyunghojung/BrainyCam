package com.hyunnyapp.brainycam.ble;

import android.bluetooth.BluetoothDevice;

public interface BleConnectionManagerDelegate {
	public void connected(BleConnectionManager manager, BluetoothDevice device);
	public void disconnected(BleConnectionManager manager, BluetoothDevice device);
	public void failToConnect(BleConnectionManager manager, BluetoothDevice device);
	public void receivedData(BleConnectionManager manager, BluetoothDevice device, String data);
}
