package com.example.pecanventures.bluetoothdiscoveryexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ServiceConnection {

    public static final String TAG = MainActivity.class.getCanonicalName();

    private static final int REQUEST_NEEDED_PERMISSIONS = 42;

    private Intent mServiceIntent;
    private Messenger outComingMessenger;

    private LinearLayoutManager devicesLLM;
    private BLEDeviceAdapter devicesAdapter;
    private List<BLEDeviceModel> devices;

    private RecyclerView discoveredDevices;
    private Button startDiscoveryBtn;

    private boolean isReady = false;
    private boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        discoveredDevices = (RecyclerView) findViewById(R.id.discovered_devices);
        startDiscoveryBtn = (Button) findViewById(R.id.start_discovery_btn);

        mServiceIntent = new Intent(this, BluetoothService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(mServiceIntent);
        bindService();
        isReady = false;

        devices = new ArrayList<BLEDeviceModel>();
        devicesLLM = new LinearLayoutManager(MainActivity.this);
        devicesLLM.setOrientation(LinearLayoutManager.VERTICAL);

        discoveredDevices.setLayoutManager(devicesLLM);
//        just dummy to check recycleview and how it shows data
//        devices.add(new BLEDeviceModel("name", "address1"));
//        devices.add(new BLEDeviceModel("name", "address2"));
//        devices.add(new BLEDeviceModel("name", "address3"));
//        devices.add(new BLEDeviceModel("name", "address4"));
//        devicesAdapter.updateData(devices);

        devicesAdapter = new BLEDeviceAdapter(MainActivity.this, devices, new BLEDeviceAdapter.ItemOnClickListener() {
            @Override
            public void onItemClick(View v, int position) {

                // uncomment to save device and autoconnect to it
//                SharedPreferences pref = getSharedPreferences(MainActivity.TAG, 0);
//                String address = devices.get(position).getAddress();
//                pref.edit().putString(Constants.PREF_AUTO_CONNECT_TO_ADDRESS, address).commit();
//                Log.d(TAG, "save device to autoconnect");
//                sendMessageToService(Constants.MSG_CONNECT_TO_SAVED_DEVICE);
            }
        });

        Log.d(TAG, "devicesAdapter.size="+devicesAdapter.getItemCount());
        discoveredDevices.setAdapter(devicesAdapter);
        devicesAdapter.notifyDataSetChanged();

        startDiscoveryBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // check is have BLE feature
                if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                    Log.d(TAG, "have no system feature FEATURE_BLUETOOTH_LE");
                    Toast.makeText(MainActivity.this, getString(R.string.msg_have_no_ble_sys_feature), Toast.LENGTH_LONG).show();
                    finish();
                }

                // Try to init Bluetooth
                BluetoothManager bluetoothManager = null;
                if(android.os.Build.VERSION.SDK_INT >= 18) {
                    bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                    if (bluetoothManager == null) {
                        Toast.makeText(MainActivity.this, getString(R.string.msg_couldnt_init_bt), Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }

                // Try to get phone's bluetooth adapter
                BluetoothAdapter bluetoothAdapter = null;
                if(android.os.Build.VERSION.SDK_INT >= 18) {
                    bluetoothAdapter = bluetoothManager.getAdapter();
                } else bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                // if device doesn't have bluetooth adapter
                if(bluetoothAdapter == null) {
                    Toast.makeText(MainActivity.this, getString(R.string.msg_have_no_bluetooth), Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }

                // request user to enable bluetooth
                if(!bluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, Constants.REQUEST_ENABLE_BT);
                } else if(isBound()) {
                    if (checkAllPermissions()) {
                        sendMessageToService(Constants.MSG_PERMISSION_GRANTED);
                        Log.d(TAG, "sendMessageToService(Constants.MSG_PERMISSION_GRANTED)");
                        // start discovery and autoconnect if user choosed device before
                        sendMessageToService(Constants.MSG_INIT_BLE);
                        Log.d(TAG, "sendMessageToService(Constants.MSG_INIT_BLE)");
                    }
                }
            }
        });


    }

    private void requestPermissions() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION }, REQUEST_NEEDED_PERMISSIONS);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        requestPermissions();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Callback on requests:
        // 1) request to enable Bluetooth
        if(requestCode == Constants.REQUEST_ENABLE_BT) {
            if(resultCode == RESULT_OK) {
                BluetoothAdapter bluetoothAdapter = null;
                if(android.os.Build.VERSION.SDK_INT >= 18) {
                    BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
                    bluetoothAdapter = bluetoothManager.getAdapter();
                } else bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
                bluetoothAdapter.enable();
                if(isBound() && checkAllPermissions()) {
                    sendMessageToService(Constants.MSG_PERMISSION_GRANTED);
                    sendMessageToService(Constants.MSG_INIT_BLE);
                }
            } else {
                Toast.makeText(this, getString(R.string.msg_you_must_enable_bt), Toast.LENGTH_LONG).show();
            }

        } // 2) request to approve permissions
         else if(requestCode == REQUEST_NEEDED_PERMISSIONS) {
            if(!checkAllPermissions()) requestPermissions();
        }
    }

    private boolean checkAllPermissions() {
        return (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                == PackageManager.PERMISSION_GRANTED
                // need in couple with bluetooth permission
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService();
    }

    public void openApplicationSettings() {
        Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(appSettingsIntent, REQUEST_NEEDED_PERMISSIONS);
    }

    // Requested permissions callback, check that all needed permissions are granted
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        boolean allGranted = true;
        if(requestCode == REQUEST_NEEDED_PERMISSIONS) {
            for (int i = 0; i < 3; i++) {
                if(grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    openApplicationSettings();
                    Toast.makeText(this, "Please grant all permissions.", Toast.LENGTH_LONG).show();
                    continue;
                }
                if(grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions();
                    allGranted = false;
                }
            }
            if(allGranted) {
                sendMessageToService(Constants.MSG_PERMISSION_GRANTED);
                Log.d(TAG, "sendMessageToService(Constants.MSG_PERMISSION_GRANTED)");
            }
        }
    }

    protected synchronized void unbindService() {
        if (!isBound()) {
            return;
        }
        // lock object (prevents access to service while disconnecting)
        synchronized (outComingMessenger) {
            sendMessageToService(Message.obtain(null, Constants.MSG_UNREGISTER_CLIENT));
            unbindService(this);
            outComingMessenger = null;
        }
    }

    protected void bindService() {
        bindService(mServiceIntent, this, BIND_AUTO_CREATE);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        outComingMessenger = new Messenger(service);

        // connect to service with messenger and give him our "address" to send messages back/response
        final Message msg = Message.obtain(null, Constants.MSG_REGISTER_CLIENT);
        msg.replyTo = inComingMessenger;
        msg.obj = getClass().getSimpleName();
        sendMessageToService(msg);

        sendMessageToService(Constants.MSG_CHECK_CONNECTION_STATUS);
    }


    @Override
    public void onServiceDisconnected(ComponentName name) {
        outComingMessenger = null;
        bindService();
    }

    /**
     * Send message to service
     */
    protected void sendMessageToService(Message msg) {
        if (!isBound()) {
            Log.d(TAG, "sendMessageToService but service is not connected!!");
            return;
        }
        try {
            msg.replyTo = inComingMessenger;
            outComingMessenger.send(msg);
        } catch (RemoteException e) {
            Log.e(TAG, "sendMessageToService exception while sending message", e);
        }
    }

    /**
     * Send simple message to connected service
     * @param messageId
     */
    protected void sendMessageToService(int messageId) {
        sendMessageToService(Message.obtain(null, messageId));
    }

    /**
     * Service is connected?
     */
    protected final boolean isBound() {
        return outComingMessenger != null;
    }

    @SuppressLint("HandlerLeak")
    private final Handler mIncomingHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.MSG_BLE_CONNECTION_STATUS:
                    int res = (int) msg.obj;
                    isReady = res == BluetoothService.STATE_CONNECTED;
                    if(isReady) {
                        // connected and we able to get data from remote device
                        sendMessageToService(Constants.MSG_RPI_STATUS);
                    } else {
                        // not connected to remote device
                    }
                    break;

                case Constants.MSG_CONNECTION_READY:
                    isReady = (boolean) msg.obj;
                    if(isReady) {
                        // connected and we able to get data from remote device
                        sendMessageToService(Constants.MSG_RPI_STATUS);
                    } else {
                        // not connected to remote device
                    }
                    break;

                case Constants.MSG_SHOW_TOAST:
                    // to show message got from service
                    Toast.makeText(MainActivity.this, (String)msg.obj, Toast.LENGTH_LONG).show();
                    break;

                case Constants.MSG_BLE_DEVICE_FOUND:
                    // we discovered new device and add it to list/recycleview to show
                    BluetoothDevice device = (BluetoothDevice)msg.obj;
                    SharedPreferences pref = getSharedPreferences(MainActivity.TAG, 0);
                    String deviceAddress = pref.getString(Constants.PREF_AUTO_CONNECT_TO_ADDRESS, null);
                    // check if don't save device for autoconnection
                    if(deviceAddress == null) {

                    }
                    // filter in case if we already founded this device
                    boolean isContain = false;
                    for (BLEDeviceModel model :
                            devices) {
                        if(model.getAddress().equals(device.getAddress())) {
                            isContain = true;
                            break;
                        }
                    }
                    // add to list if device wasn't found previously
                    if(!isContain) {
                        devices.add(new BLEDeviceModel(device.getName(), device.getAddress()));
                        devicesAdapter.updateData(devices);
                    }

                    break;

                case Constants.MSG_IS_RUNNING:
                    // response on "if remote device is running/doing_some_work"
                    isRunning = (boolean)msg.obj;
                    if(isRunning) {
                        // yes, remote device doing our stuff right now
                    }
                    break;
            }
        }
    };

    final private Messenger inComingMessenger = new Messenger(mIncomingHandler);

}
