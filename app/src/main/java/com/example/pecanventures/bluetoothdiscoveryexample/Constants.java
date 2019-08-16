package com.example.pecanventures.bluetoothdiscoveryexample;

import java.util.UUID;

public final class Constants {

    public static final int MSG_PERMISSION_GRANTED = 47;
    public static final int MSG_UNREGISTER_CLIENT = 48;
    public static final int MSG_CONNECTION_READY = 49;
    public static final int MSG_CHECK_CONNECTION_STATUS = 50;
    public static final int MSG_REGISTER_CLIENT = 51;
    public static final int MSG_BLE_DEVICE_FOUND = 55;
    public static final int MSG_SHOW_TOAST = 56;
    public static final int MSG_INIT_BLE = 57;
    public static final int MSG_CONNECT_TO_SAVED_DEVICE = 58;
    public static final int MSG_BLE_CONNECTION_STATUS = 59;
    public static final int MSG_RPI_STATUS = 60;
    public static final int MSG_IS_RUNNING = 63;

    public static final int REQUEST_ENABLE_BT = 526;
    public static final int DEVICE_TIMEOUT = 55;// 55ms
    public static final int BLE_SCAN_PERIOD = 20*1000; // 20 sec
    public static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    // if we work with BLE, here example of UUIDs of GATT service and its characteristics
    public static final String GATT_SERVICE = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String SHOTS_CHARACTERISTIC = "0000ffe1-0000-1000-8000-00805f9b34fb";
    public static final String MJ_CHARACTERISTIC = "0000ffe2-0000-1000-8000-00805f9b34fb";
    public static final String HZ_CHARACTERISTIC = "0000ffe3-0000-1000-8000-00805f9b34fb";
    public static final String TOTAL_SHOTS_CHARACTERISTIC = "0000ffe4-0000-1000-8000-00805f9b34fb";
    public static final String START_CHARACTERISTIC = "0000ffe5-0000-1000-8000-00805f9b34fb";
    public static final String PREF_AUTO_CONNECT_TO_ADDRESS = "pref_autoconnect_to_addresss";

}
