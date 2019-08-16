package com.example.pecanventures.bluetoothdiscoveryexample;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import me.aflak.bluetooth.Bluetooth;
import me.aflak.bluetooth.BluetoothCallback;
import me.aflak.bluetooth.DeviceCallback;
import me.aflak.bluetooth.DiscoveryCallback;

import static android.support.v4.app.NotificationCompat.PRIORITY_MIN;


public class BluetoothService extends Service implements IResponceCallback {

    public static final String TAG = BluetoothService.class.getCanonicalName();
    public static final int STATE_CONNECTED = 1;
    public static final int STATE_CONNECTING = 2;
    public static final int STATE_DISCONNECTED = 3;

    private ArrayList<Messenger> mClients = new ArrayList<>();
    private ConnectionThread mConnectionThread = null;

    private List<BluetoothDevice> bleDevices;
    private BluetoothGattService gattService;
    private BluetoothGatt gatt;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattDescriptor gattDescriptor;
    private BluetoothLeAdvertiser bleAdvertiser;
    private BroadcastReceiver receiver;
    private BroadcastReceiver bluetoothStatusReceiver;
    private ConnectionThread thread;
    private String lastCommand;
    private boolean permissionGranted = false;

    private Bluetooth bluetooth = null;


    private int state = STATE_DISCONNECTED;


    public BluetoothService() {
    }

    @SuppressLint("HandlerLeak")
    final Handler mIncomingHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {

            switch (msg.what) {
                case Constants.MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    Log.d(TAG, "handleMessage: new client connected. Total: " + mClients.size());
                    break;

                case Constants.MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    Log.d(TAG, "handleMessage: client disconnected. Total: " + mClients.size());
                    break;

                case Constants.MSG_PERMISSION_GRANTED:
                    Log.d(TAG, "handleMessage: MSG_PERMISSION_GRANTED");
                    permissionGranted = true;
                    break;

                case Constants.MSG_CHECK_CONNECTION_STATUS:
                    Log.d(TAG, "handleMessage: MSG_CHECK_CONNECTION_STATUS");
                    sendMessageToClients(Constants.MSG_BLE_CONNECTION_STATUS, getState());
                    break;

                case Constants.MSG_INIT_BLE:
                    Log.d(TAG, "handleMessage: MSG_INIT_BLE");
                    initBle();
                    break;

                case Constants.MSG_CONNECT_TO_SAVED_DEVICE:
                    Log.d(TAG, "handleMessage: MSG_CONNECT_TO_SAVED_DEVICE");
                    connectToSavedDevice();
                    break;

//                case Constants.MSG_EXIT:
//                    Log.d(TAG, "handleMessage: exit");
//                    exit = true;
//                    sendMessageToClients(Constants.MSG_EXIT, null);
////                    onDestroy();
//                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT
//                            && android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//                        stopForeground(true);
////                        stopSelf(777);
//                        stopSelf();
//
//                    } else if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
//                        stopForeground(STOP_FOREGROUND_REMOVE);
////                        stopSelf(777);
//                        stopSelf();
//                    }
//                    break;
            }
        }
    };

    public int getState() {
        return state;
    }

    private void setState(int state) {
        this.state = state;
        if(state == STATE_DISCONNECTED) {
            sendMessageToClients(Constants.MSG_CONNECTION_READY, false);
//            if(thread!=null) {
//                thread.cancel();
//                thread = null;
//            }
        }
        if(state == STATE_CONNECTED) {
            sendMessageToClients(Constants.MSG_CONNECTION_READY, true);
        }
    }

    final private Messenger inComingMessenger = new Messenger(mIncomingHandler);

    /**
     * Send message to connected activities
     */
    public void sendMessageToClients(int msgSignal, Object obj) {
        if (mClients.size() == 0)
            return;

        sendMessage(mClients.get(0), Message.obtain(null, msgSignal, obj));

        for (int i = 1; i < mClients.size(); i++) {
            if (mClients.get(i) == null)
                continue;
            sendMessage(mClients.get(i), Message.obtain(null, msgSignal, obj));
        }
    }

    /**
     * Send message to binded activity
     */
    private void sendMessage(Messenger msgr, Message msg) {
        try {
            msgr.send((msg));
        } catch (RemoteException e) {
            Log.e(TAG, "can't send message", e);
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return inComingMessenger.getBinder();
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(){
        String channelID = "QadradGameChangerNotificationChannelID";
        String channelName = "QadradGameChangerNotificationChannelName";
        NotificationChannel chan = new NotificationChannel(channelID,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(0x000000FF);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelID;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(BluetoothService.this, MainActivity.class)
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER), PendingIntent.FLAG_CANCEL_CURRENT);

        Notification notification;
        if (Build.VERSION.SDK_INT < 26) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setPriority(PRIORITY_MIN)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent);
            notification = builder.build();
        } else {
            String channel = createNotificationChannel();
            Notification.Builder builder = new Notification.Builder(this, channel)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(pendingIntent);
            notification = builder.build();
        }
        startForeground(777, notification);

//        Intent hideIntent = new Intent(this, HideNotificationService.class);
//        startService(hideIntent);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
                final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

                Log.d(TAG, "Bond state changed for: " + device.getAddress() + " new state: " + bondState
                        + " previous: " + previousBondState);

                // skip other devices
                SharedPreferences pref = getSharedPreferences(MainActivity.TAG, 0);
                String address = pref.getString(Constants.PREF_AUTO_CONNECT_TO_ADDRESS, null);
                Log.d(TAG, "Saved address: "+address);
                if (!device.getAddress().equals(address))
                    return;

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    // Continue to do what you've started before
                    connectToSavedDevice();

//                    mContext.unregisterReceiver(this);
//                    mCallbacks.onBonded();
                }
            }
        };
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//        registerReceiver(receiver, filter);

        bluetoothStatusReceiver = new BluetoothStatusReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        registerReceiver(bluetoothStatusReceiver, filter);

        if(android.os.Build.VERSION.SDK_INT>=18) {
            bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
        } else bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

//        runBluetoothAutoconnection();
    }

    private void initBle() {
        bleDevices = new ArrayList<>();

        if(!checkAllPermissions()){
            Log.d(TAG, "no BT permission granted");
            return;
        }

        if (bluetoothAdapter == null) {
            Log.d(TAG, "bluetoothAdapter is null");
            sendMessageToClients(Constants.MSG_SHOW_TOAST, getString(R.string.msg_have_no_bluetooth));
//            stopSelf();
            return;
        }
        if(!bluetoothAdapter.isEnabled()) {
            Log.d(TAG, "bluetoothAdapter is not enabled");
            sendMessageToClients(Constants.MSG_SHOW_TOAST, getString(R.string.msg_you_must_enable_bt));
//            stopSelf();
            return;
        }

        if(bluetooth == null) {
            bluetooth = new Bluetooth(this);


            bluetooth.setBluetoothCallback(new BluetoothCallback() {
                @Override
                public void onBluetoothTurningOn() {}

                @Override
                public void onBluetoothOn() {}

                @Override
                public void onBluetoothTurningOff() {
                    bluetooth = null;
                }

                @Override
                public void onBluetoothOff() {
//                    bluetooth = null;
                }

                @Override
                public void onUserDeniedActivation() {
                    // when using bluetooth.showEnableDialog()
                    // you will also have to call bluetooth.onActivityResult()
                }
            });

            bluetooth.setDiscoveryCallback(new DiscoveryCallback() {
                @Override public void onDiscoveryStarted() {
                    setState(STATE_CONNECTING);
                }
                @Override public void onDiscoveryFinished() {
                    bleDevices.clear();
                    setState(STATE_DISCONNECTED);
                    Log.d(TAG, "Discovery finished");
                    sendMessageToClients(Constants.MSG_SHOW_TOAST, "Discovery finished");

                }
                @Override public void onDeviceFound(BluetoothDevice device) {
                    if(bleDevices.indexOf(device)<0) {
                        bleDevices.add(device);
                        Log.d(TAG, "Found new device while scanning: "+device.getAddress());
                        sendMessageToClients(Constants.MSG_BLE_DEVICE_FOUND, device);
                    }
                }
                @Override public void onDevicePaired(BluetoothDevice device) {}
                @Override public void onDeviceUnpaired(BluetoothDevice device) {}
                @Override public void onError(String message) {
                    Log.e(TAG, "DiscoveryCallback onError "+message);
                }
            });
            bluetooth.setDeviceCallback(new DeviceCallback() {
                @Override public void onDeviceConnected(BluetoothDevice device) {
                    Log.d(TAG, "Connected to device: "+device.getAddress());
                    setState(STATE_CONNECTED);
                }
                @Override public void onDeviceDisconnected(BluetoothDevice device, String message) {
                    Log.d(TAG, "Disconnected with device: "+device.getAddress() + "\n"+message);
                    setState(STATE_DISCONNECTED);
                }
                @Override public void onMessage(String message) {
                    response(message.replaceAll(" ",""));
                    Log.d(TAG, message);
//                    setState(STATE_DISCONNECTED);
                }
                @Override public void onError(String message) {
                    Log.e(TAG, "DeviceCallback onError "+message);
                }
                @Override public void onConnectError(BluetoothDevice device, String message) {
                    Log.e(TAG, "DeviceCallback onConnectError "+message);
                    setState(STATE_DISCONNECTED);

                }
            });
            bluetooth.onStart();
//            bluetooth.enable();
        }

        connectToSavedDevice();
    }

    private void connectToSavedDevice() {
        // this method uses shared preferences where we saved found/choosed by user device
        Log.d(TAG, "connectToSavedDevice state="+getState());
        if(getState() != STATE_DISCONNECTED) return;
        SharedPreferences pref = getSharedPreferences(MainActivity.TAG, 0);
        String address = pref.getString(Constants.PREF_AUTO_CONNECT_TO_ADDRESS, null);
        if(address == null) {
            Log.d(TAG, "saved address==null start scan for devices");
            setState(STATE_DISCONNECTED);
            scanDevices();
            return;
        }
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if(device!=null) {
//            if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
            setState(STATE_CONNECTING);
            Log.d(TAG, "device found try to connect/bound, connect to rPI");

            bluetooth.connectToAddress(address,false);
//            bluetooth.connectToAddress(address,true);

        }
    }

    private boolean checkAllPermissions() {
        return (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void response(String resp) {
        if(resp == null || "".equals(resp)) return;
        // parse response
    }

    @Override
    public void stateChanged(int state) {
        Log.d(TAG, "state="+state);
        setState(state);
    }

    private void scanDevices() {
        if(getState()==STATE_DISCONNECTED) {
            bluetooth.startScanning();
            Log.d(TAG, "BT scan started");
        }
    }

    // Send text message to remote device
    private void sendMessage(String msg) {
        if (getState() != STATE_CONNECTED) {
            Log.d(TAG, "device disconnected");
            return;
        }
        try {
            lastCommand=msg;
            bluetooth.send(msg);
        } catch (Exception e) {
            Log.e(TAG, "msg="+msg, e);
        }

        Log.d(TAG, "sendMessage:"+msg);
    }

    @Override
    public void onDestroy() {
        if(thread!=null) {
            thread.cancel();
            thread = null;
        }

        if(scheduler!=null) {
            scheduler.shutdownNow();
        }

        if(bluetooth!=null) {
//            bluetooth.disable();
            bluetooth.disconnect();
            bluetooth.onStop();
            bluetooth = null;
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            stopForeground(0);
        } else {
            stopForeground(true);
        }

        unregisterReceiver(bluetoothStatusReceiver);

        Log.d(TAG, "onDestroy");

//        if (exit) {
//            Log.d(TAG, "RecordingService/onDestroy exit");
//            exit = false;
//            sendMessageToClients(Constants.MSG_FINISH_ACTIVITIES, null);
//        }
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved: killed by system.");
    }

    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1);

    // uncomment in case if needs autoconnection mechanism
//    private void runBluetoothAutoconnection() {
//        Log.d(TAG, "run BLE autoconnection");
//        try {
//            scheduler.scheduleAtFixedRate(new Runnable() {
//
//                public void run() {
//
//                    Log.d(TAG, "Attempt of autoconnect, state="+getState());
//                    if(getState()==STATE_DISCONNECTED) initBle();
//
//                }
//            }, 2 * 1000L, 35 * 1000L, TimeUnit.MILLISECONDS);
//        } catch (RejectedExecutionException e) {
//            Log.e(TAG, "BluetoothService/init do not connect by scheduler, STATE_CONNECTING", e);
//            e.printStackTrace();
//        }
//    }

    public class BluetoothStatusReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "BluetoothStatusReceiver/onReceive: " + intent.getAction());
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(intent.getAction())) {
                sendMessageToClients(Constants.MSG_SHOW_TOAST, "Discovery finished");
//                bleDevices.clear();
//                Log.d(TAG, "Discovery finished");
            } else if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE,
                        BluetoothAdapter.STATE_DISCONNECTED);
                if(BluetoothAdapter.STATE_CONNECTED == state) {
                    setState(STATE_CONNECTED);
                } else if(BluetoothAdapter.STATE_CONNECTING == state) {
                    setState(STATE_CONNECTING);
                } else if(BluetoothAdapter.STATE_DISCONNECTING == state) {

                } else if(BluetoothAdapter.STATE_DISCONNECTED == state) {
                    setState(STATE_DISCONNECTED);
                }
            }  else if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(bleDevices.indexOf(device)<0) {
                    bleDevices.add(device);
                    Log.d(TAG, "Found new device while scanning: "+device.getAddress());
                    sendMessageToClients(Constants.MSG_BLE_DEVICE_FOUND, device);
                }
            }
        }
    }
    
}

