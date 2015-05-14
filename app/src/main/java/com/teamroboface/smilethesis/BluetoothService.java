package com.teamroboface.smilethesis;

/**
 * Created by heirlab4 on 5/14/15.
 */
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * This thread runs while listening for incoming connections. It behaves
 * like a server-side client. It runs until a connection is accepted
 * (or until cancelled).
 */
public class BluetoothService extends Thread {
    // Debugging
    private static final String TAG = "BluetoothService";

    //Unique UUID for this application
    private static final UUID SMILE_UUID = UUID.fromString("0c65fe91-9412-498e-b6e7-1fcd3d3d2236");

    // Member fields
    private static BluetoothAdapter mAdapter;
    private static Handler mHandler;
    private static int mState;
    private static BluetoothDevice mDevice;
    private static BluetoothSocket mSocket;

    private static Context mContext = null;
    private static InputStream in;
    private static OutputStream out;

    private static BluetoothServerSocket mmServerSocket;
    private static AcceptThread acceptThread = null;

    // Constants that indicate the current connection state
    private static final int STATE_NONE = 0;       // we're doing nothing
    private static final int STATE_LISTEN = 1;     // now listening for incoming connections
    private static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    private static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private static boolean started = false;

    /**
     * Constructor. Prepares a new Bluetooth session.
     *
     */
    public BluetoothService(Context context) {
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mState = STATE_NONE;
        Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        context.startActivity(discoverableIntent);
    }

    public static void startBluetooth() {
        if (!started) {
            acceptThread = new AcceptThread();
            acceptThread.start();
            started = true;
        }
    }

    public static void setHandler(Handler h){mHandler = h;}

    public static void write(String string) {
        if (acceptThread != null) {
            acceptThread.write(string.getBytes());
        }
    }

    public static String read() {
        if (acceptThread != null) {
            String read = acceptThread.read();
            acceptThread.clearBuffer();
            return read;
        } else {
            return "";
        }
    }

    public static String readNext() {
        if (acceptThread != null) {
            String reply = "";
            while (reply.equals("")) {
                reply = acceptThread.read();
            }
            acceptThread.clearBuffer();
            return reply;
        } else {
            return "";
        }
    }
    public static void cancel() {
        if (acceptThread != null) {
            acceptThread.cancel();
        }
    }

    public static boolean isEnabled() {
        return mAdapter.isEnabled();
    }

    /**
     * AcceptThread is the counterpart to ConnectThread and contains the code for the server.
     * The communication part is exactly the same, write to the output stream, read from the input stream,
     * but the initial configuration is slightly different.
     * @author Kellen Carey
     */
    private static class AcceptThread extends Thread {
        private BluetoothServerSocket serverSocket;
        private InputStream in;
        private OutputStream out;
        private String lastRead = "";
        /**
         * Constructor takes no arguments, because it is not attempting to connect to a particular device.
         * Instead, it waits for someone to connect to it.
         */
        public AcceptThread() {
            try {
                serverSocket = mAdapter.listenUsingRfcommWithServiceRecord("SMILE", SMILE_UUID);
            } catch (IOException e) { }
        }
        /**
         * This method is mainly concerned with accepting an incoming connection
         */
        public void run() {
            // This is the socket over which we will be communicating
            BluetoothSocket socket = null;
            while (true) {
                try {
                    Log.w("USER", "Awaiting transmission");
                    // Listen for incoming requests, and create a socket once a request is received
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    Log.w("USER", "Fail to accept.", e);
                    break;
                }
                Log.w("USER", "A connection was accepted.");
                // Check to make sure that the socket was actually created, and accept() wasn't terminated prematurely
                if (socket != null) {
                    try {
                        Log.w("USER", "Accepted incoming transmission.");
                        // If the socket is verified, connect to it and begin communications
                        connect(socket);
                        // Since we've already connected to our client, we no longer care about listening for other connections, so we can close the serverSocket.
                        // NOTE THAT THIS DOES NOT CLOSE THE ALREADY-CREATED SOCKET, BUT ONLY THE SERVERSOCKET THAT LISTENS FOR NEW CONNECTIONS
                        serverSocket.close();
                    } catch (IOException e) {

                    }
                }
                Log.d("USER", "The session was closed. Listen again.");
            }
        }
        /**
         * This method is the one that actually takes care of the communications.
         * At this point, the code is more or less identical to the ConnectThread since the conncetions have already been made.
         * @param socket
         */
        private void connect(BluetoothSocket socket) {
            try {
                in = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }


            byte[] buffer = new byte[1024];
            Bundle bundle = new Bundle();
            int bytes = 0;
            Looper.prepare();
            Log.d(TAG, "Bluetooth Connected!!");

            while (true) {
                try {
                    // Read from input stream
                    bytes = in.read(buffer);
                    String lastlastRead = lastRead;
                    lastRead = new String(buffer, 0, bytes);
                    if (!lastRead.equals(lastlastRead)){
                        Log.d("Just Received BT:", lastRead);
                    }
                } catch (IOException e) {
                    break;
                }
            }

            Looper.loop();
        }

        public void write(byte[] bytes) {
            try {
                out.write(bytes);
            } catch (IOException e) {
                // TODO Auto-generated catch block
            } catch (NullPointerException ee) {

            }
        }

        public synchronized String read() {
            return lastRead;
        }

        public void clearBuffer() {
            lastRead = "";
        }
        /** Will cancel the listening socket, and cause the thread to finish */
        public void cancel() {
            try {
                serverSocket.close();
                Log.d("USER", "The server socket is closed.");
            } catch (IOException e) { }
        }
    }

}


