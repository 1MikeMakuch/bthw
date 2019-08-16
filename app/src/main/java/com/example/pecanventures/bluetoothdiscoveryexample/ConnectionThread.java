package com.example.pecanventures.bluetoothdiscoveryexample;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectionThread extends Thread implements ConnectionInterface {

    public static final String TAG = ConnectionThread.class.getCanonicalName();

    private BluetoothDevice mDevice;
    private BufferedInputStream mInStream = null;
    private OutputStream mOutStream = null;
    private BluetoothSocket mSocket;
    private long previousTime;
    private IResponceCallback callback;
    private boolean isThreadActive = true;

    public ConnectionThread(IResponceCallback callback, BluetoothDevice mDevice) {
        this.callback = callback;
        this.mDevice = mDevice;
    }

    @Override
    public void run() {
        Log.d(TAG,  "ConnectionThread run");

        // Connect to BT device
        try {

            mSocket = connectDeviceUsingAPI10();
            if(mSocket!=null) {
                callback.stateChanged(BluetoothService.STATE_CONNECTED);
                while (isThreadActive) {
                    final String line = readLine();
                    Log.d(TAG, "readline="+line);
                    callback.response(line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while connecting", e);
            // TODO: Make toast 'Couldn't connect, try again.'
        } finally {
            // Close the socket
            cancel();
            callback.stateChanged(BluetoothService.STATE_DISCONNECTED);
        }
    }

    public String readLine() throws IOException {
        int i = 0, z = 0, ass = 0, puppy = 0;
        /**
         * Read line from device
         */
        final byte[] buffer = new byte[1024];
        final char[] tmp = new char[200];
        synchronized (buffer) {
            final StringBuffer result = new StringBuffer("");
            String res = "";
            try {
                while (result.toString().lastIndexOf("\n") >= result.length()-2) {
                    //while (result.toString().lastIndexOf("\n") < result.length()-2) {
                    if (puppy++ > 10000 || mInStream == null) {
                        Log.e(TAG, "ass="+ass+" result.toString().contains(\"\\n\")" + result.toString().contains("\n")+" puppy="+puppy+" mInStream="+mInStream);
                        //throw new IOException();
                        return null;
                    } else {
                        sleep(1);
                    }
                    ass = mInStream.available();
                    if(ass == 0) continue;
                    z = mInStream.read(buffer, 0, ass);
                    /*String preres = new String(buffer, 0, ass);
                    Log.d(TAG, "preres="+preres);
                    result.append(preres);*/
                    result.append(new String(buffer, 0, ass, "ISO-8859-1"));
                }
            } catch (InterruptedException e) {
                throw new IOException("InterruptedException while sleep(1)");
            } catch (NullPointerException e) {
                throw new IOException("NPE"+e.getMessage());
            }
            res = result.toString();
            char ch;
            for (i = 0, z = 0; i < res.length(); i++) {
                ch = res.charAt(i);
                if (ch != '?' && ch != '>' && ch != '\r' && ch != ' ') {
                    tmp[z++] = ch;
                }
            }
            res = new String(tmp, 0, z);
            return res;
        }
    }

    private final void waitTimeout() {
        final long tDiff = System.currentTimeMillis() - previousTime;
        try {
            if (tDiff < Constants.DEVICE_TIMEOUT) {
                sleep(Constants.DEVICE_TIMEOUT - tDiff);
                previousTime = System.currentTimeMillis();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException - waitTimeout: ", e);
        }
    }

    private final void waitTime() {
        final long tDiff = System.currentTimeMillis() - previousTime;
        try {
            if (tDiff < 300) {
                sleep(Constants.DEVICE_TIMEOUT - tDiff);
                previousTime = System.currentTimeMillis();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "ConnectionThread - waitTime: ", e);
        }
    }

    public void clearInputStream() throws IOException {
        try {
            Thread.sleep(350);
            while (mInStream != null && mInStream.available() > 0) {
                mInStream.skip(1);
                Thread.sleep(10);
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "InterruptedException - clearInputStream: ", e);
        }
    }

    /**
     * Send command to device
     *
     * @param cmd
     *            command without newline at the end of string
     */
    public synchronized void sendCommand(String cmd) throws IOException {
//        mOutStream.write((cmd + "\r").getBytes());
        mOutStream.write(cmd.getBytes());
        mOutStream.flush();
        waitTime();
    }

    /**
     * Send command to device
     *
     * @param obd
     *            command without newline at the end of string
     */
    public synchronized void sendCommand(byte[] obd) throws IOException {
        mOutStream.write(obd);
//        mOutStream.write(Constants.DEVICE_EOL);
        mOutStream.flush();
//        waitTime();

        //final String line = readLine();
        //Log.d(TAG, "readline="+line);
        //callback.response(line);
    }

    public void cancel() {
        try {
            Log.d(TAG, "ConnectionThread/cancel()");
            if (mSocket != null) {
                mSocket.close();
                // sleep(500);
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while cancel", e);
        }
        mSocket = null;
        mInStream = null;
        mOutStream = null;
        interrupt();
    }

    @TargetApi(10)
    private BluetoothSocket connectDeviceUsingAPI10() throws IOException {
        BluetoothSocket socket = null;
        IOException ioex = null;

/*        ioex = null;
        socket = null;
        // way #0. Using standard secure connection procedure via UUID
        try {
//            if (!isThreadActive)
//                return null;
            Log.d(TAG, "Try via API10: createRfcommSocketToServiceRecord way 0");
            socket = mDevice
                    .createRfcommSocketToServiceRecord(Constants.SPP_UUID);// RFCOMM_UUID);//SPP_UUID);SPP_UUID);
        } catch (IOException e) {
            ioex = e;
        }
        if (socket != null && ioex == null) {
            try {
                socket.connect();
                setStreams(socket.getOutputStream(), socket.getInputStream());
//                trySocket();
//                if (VERBOSE_DEBUG)
                Log.d(TAG, "Connected via API10: createRfcommSocketToServiceRecord");
            } catch (IOException ex) {
                ioex = ex;
                // if (VERBOSE_DEBUG)
                Log.d(TAG, "Connection via API10: createRfcommSocketToServiceRecord "
                        + ex.getMessage());
                try {
                    socket.close();
                } catch (IOException e) {
                } finally {
                    socket = null;
                }
            }
        }
        if (socket != null && ioex == null) {
            return socket;
        }*/

        int port = 1;
        // for (port = 1; port < 30; port++) {
        ioex = null;
        socket = null;
        // way #1. Connect using workaround for Android < 2.3
        try {
//            if (!isThreadActive)
//                return null;
            Log.d(TAG, "Try via API10: createInsecureRfcommSocketToServiceRecord way 1");
            socket = mDevice
                    .createInsecureRfcommSocketToServiceRecord(Constants.SPP_UUID); // RFCOMM_UUID);
        } catch (Exception e) { // Other exceptions will be IOException
            if (e instanceof IOException)
                ioex = (IOException) e;
        }
        if (socket != null && ioex == null) {
            try {
                socket.connect();
                setStreams(socket.getOutputStream(), socket.getInputStream());
//                trySocket();
                Log.d(TAG, "Connected via API10: createInsecureRfcommSocketToServiceRecord");
                Log.d(TAG,"mSocket: " + socket.toString());
            } catch (IOException ex) {
                ioex = ex;
                // if (VERBOSE_DEBUG)
                Log.d(TAG, "Connection via API10: createInsecureRfcommSocketToServiceRecord "
                        + ex.getMessage());
                try {
                    socket.close();
                } catch (IOException e) {
                } finally {
                    socket = null;
                }
            }
        }
        if (socket != null && ioex == null) {
            return socket;

        }

        /*// way #2. Using hidden api procedure with insecure socket
        socket = null;
        ioex = null;
        // Try to fallback to API5 method
        try {
//            if (!isThreadActive)
//                return null;
            Log.d(TAG, "Try via API10: createInsecureRfcommSocket way 2");
            Method m = mDevice.getClass().getMethod(
                    "createInsecureRfcommSocket", new Class[] { int.class });
            socket = (BluetoothSocket) m.invoke(mDevice, Integer.valueOf(port));
        } catch (Exception e) { // Other exceptions will be IOException
            if (e instanceof IOException)
                ioex = (IOException) e;
        }
        if (socket != null && ioex == null) {
            try {
                socket.connect();
                setStreams(socket.getOutputStream(), socket.getInputStream());
//                trySocket();
//                if (VERBOSE_DEBUG)
                Log.d(TAG, "Connected via API10: createInsecureRfcommSocket");
            } catch (IOException ex) {
                ioex = ex;
                // if (VERBOSE_DEBUG)
                Log.d(TAG, "Connection via API10: createInsecureRfcommSocket "
                        + ex.getMessage());
                try {
                    socket.close();
                } catch (IOException e) {
                } finally {
                    socket = null;
                }
            }
        }

        if (socket != null && ioex == null) {
            return socket;
        }

        ioex = null;
        socket = null;
        // way #3. Connect using workaround for Android < 2.3
        try {
//            if (!isThreadActive)
//                return null;
            Log.d(TAG, "Try via API10: createRfcommSocket way 3");
            Method m = mDevice.getClass().getMethod("createRfcommSocket",
                    new Class[] { int.class });
            socket = (BluetoothSocket) m.invoke(mDevice, Integer.valueOf(port));
        } catch (Exception e) { // Other exceptions will be IOException
            if (e instanceof IOException)
                ioex = (IOException) e;
        }
        if (socket != null && ioex == null) {
            try {
                socket.connect();
                setStreams(socket.getOutputStream(), socket.getInputStream());
//                trySocket();
                Log.d(TAG, "Connected via API10: createRfcommSocket");
                Log.d(TAG, "mSocket: " + socket.toString());
            } catch (IOException ex) {
                ioex = ex;
                // if (VERBOSE_DEBUG)
                Log.e(TAG,"Connection via API10: createRfcommSocket "
                        + ex);
                try {
                    socket.close();
                } catch (IOException e) {
                } finally {
                    socket = null;
                }
            }
        }
        if (socket != null && ioex == null) {
            return socket;
        }
        // }*/
        return socket;
    }

    private void setStreams(OutputStream out, InputStream in) {
        this.mOutStream = out;
        this.mInStream = new BufferedInputStream(in);
    }

}
