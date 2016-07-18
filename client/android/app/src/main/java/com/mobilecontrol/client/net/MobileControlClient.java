package com.mobilecontrol.client.net;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.UnknownHostException;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

public class MobileControlClient {
    private static final String TAG = "MobileControlClient";

    private static final String MULTI_CAST_ADDR = "228.5.6.7";
    private static final int SERVER_UDP_PORT = 30000;//multicast send to
    private static final int SERVER_TCP_PORT = 27015;//control data send to
    private static final int LOCAL_UDP_PORT = 28960;//listen multicast here

    private static final short DISCONNECTED = 0;
    private static final short CONNECTING = 1;
    private static final short CONNECTED = 2;

    private NetworkThread mWorkingThread;
    private Handler mWTHanlder;
    private OnConnectListener mOnConnectListener;
//    private boolean mConnected = false;
    private short mConnectionStatus = DISCONNECTED;

    public MobileControlClient() {
        mWorkingThread = new NetworkThread("MobConClientNetThread");
        mWorkingThread.start();
        mWTHanlder = new Handler(mWorkingThread.getLooper()) {

            @Override
            public void handleMessage(Message msg) {
                Log.d(TAG, "mWTHanlder handle msg: " + msg.what);
                switch(msg.what) {
                case NetworkThread.MSG_CONNECT:
                    mWorkingThread.connect();
                    break;
                case NetworkThread.MSG_SEND:
                    mWorkingThread.send((String)msg.obj);
                    break;
                default:
                    break;
                }
            }

        };

//        connect();
    }

    private void clearHandlerMsgs() {
        mWTHanlder.removeMessages(NetworkThread.MSG_CONNECT);
        mWTHanlder.removeMessages(NetworkThread.MSG_SEND);
    }

    public void setOnConnectListener(OnConnectListener listener) {
        mOnConnectListener = listener;
    }

    public void connect() {
        Log.d(TAG, "connect");
        clearHandlerMsgs();
        mWTHanlder.sendEmptyMessage(NetworkThread.MSG_CONNECT);
    }

    public void disconnect() {
        clearHandlerMsgs();
        mWorkingThread.disconnect();
//        mWorkingThread.quitSafely();
    }

    public void send(String content) {
        Log.d(TAG, "send: " + content);
        clearHandlerMsgs();
        mWTHanlder.sendMessage(Message.obtain(mWTHanlder,
                NetworkThread.MSG_SEND, content));
    }

    public boolean isConnected() {
        return mWorkingThread.isConnected();
    }

    public interface OnConnectListener {
        void onConnectStateChanged(boolean connected);
    }

    public class NetworkThread extends HandlerThread {

        private static final String TAG = "NetworkThread";

        public static final int MSG_CONNECT = 0;
        public static final int MSG_SEND = 1;

        private Socket mClientSoc;
        private String mServerIp;// = "172.17.106.55";
        private DataOutputStream out;

        public NetworkThread(String name) {
            super(name);
        }

        private void connect() {
            mConnectionStatus = CONNECTING;
            Log.d(TAG, "connect");
            String res = null;
//            do {
                wave();
                res = waitForWaveBack();
//            } while (TextUtils.isEmpty(res));

                if (!TextUtils.isEmpty(res)) {
                    String serverIp = res.substring(res.indexOf("/") + 1);
//                  connect(serverIp, SERVER_TCP_PORT);
                    mServerIp = serverIp;
//                    mConnected = true;
                    mConnectionStatus = CONNECTED;
                    mOnConnectListener.onConnectStateChanged(!TextUtils.isEmpty(mServerIp));
                }
        }

        private void connect(String serverName, int port) {
            try {
                Log.d(TAG, "Connecting to server " + serverName + " on port " + port);
                mClientSoc = new Socket(serverName, port);
                mClientSoc.setKeepAlive(true);
                Log.d(TAG,
                        "Just connected to server " + mClientSoc.getRemoteSocketAddress());
            } catch (UnknownHostException e) {
                Log.e(TAG, "xxxxxxxxxxxxxxxxx", e);
            } catch (IOException e) {
                Log.e(TAG, "xxxxxxxxxxxxxxxxx", e);
            }
        }

        private void send(String content) {
            Log.d(TAG, "send: " + content + ", mServerIp: " + mServerIp);
            if (TextUtils.isEmpty(mServerIp)) {
                Log.e(TAG, "send: server ip null!");
                return;
            }
            try {
//                if (mClientSoc == null) {
                    connect(mServerIp, SERVER_TCP_PORT);
//                }

//                if (out == null) {
                    OutputStream outToServer = mClientSoc.getOutputStream();
                    out = new DataOutputStream(outToServer);
//                }

                out.writeUTF(/*"Content: " + */content/* + " from "
                        + mClientSoc.getLocalSocketAddress()*/);

//                InputStream inFromServer = mClientSoc.getInputStream();
//                DataInputStream in = new DataInputStream(inFromServer);
//                Log.d(TAG, "Server says " + in.readUTF());
//                out.close();
//                mClientSoc.close();
            } catch (IOException e) {
                Log.e(TAG, "exception on send: ", e);
                disconnect();
            }
        }

        private void wave() {
            try {
                sendMulticast("hi_i_am_client", MULTI_CAST_ADDR, SERVER_UDP_PORT);
                Log.d(TAG, "wave to server sent");
            } catch (IOException e) {
                Log.e(TAG, "sendMulticast exception: ", e);
            }
        }

        private String waitForWaveBack() {
            String res = null;
            try {
                res = receiveMulticast(MULTI_CAST_ADDR, LOCAL_UDP_PORT);
                Log.d(TAG, "waitForWaveBack, got res: " + res);
            } catch (IOException e) {
                Log.e(TAG, "receiveMulticast exception: ", e);
            }
            return res;
        }

        private void sendMulticast(String sendMessage, String addr, int port) throws IOException {
            Log.d(TAG, "sendMulticast");
            InetAddress inetAddress = InetAddress.getByName(addr);
            DatagramPacket datagramPacket = new DatagramPacket(
                    sendMessage.getBytes(), sendMessage.length(), inetAddress,
                    port);
            MulticastSocket multicastSocket = new MulticastSocket();
            multicastSocket.send(datagramPacket);
            multicastSocket.close();
        }

        private String receiveMulticast(String addr, int port) throws IOException {
            Log.d(TAG, "receiveMulticast");
            InetAddress group = InetAddress.getByName(addr);
            MulticastSocket s = new MulticastSocket(port);
            byte[] arb = new byte[1024];
            s.joinGroup(group);
            String res;
            while (mConnectionStatus == CONNECTING) {
                DatagramPacket datagramPacket = new DatagramPacket(arb,
                        arb.length);
                Log.d(TAG, "before receive");
                s.receive(datagramPacket);
                res = new String(arb);
                Log.d(TAG, "after receive, res: " + res);
                if (res.startsWith("hi_i_am_server")) {
                    String ret = datagramPacket.getAddress().toString();
                    Log.d(TAG, "server waved back, ip: " + ret);
//                    String ret = s.getRemoteSocketAddress().toString();
                    s.close();
                    return ret;
                }
            }
            return null;
        }

        public void disconnect() {
            if (mClientSoc != null) {
                try {
                    mClientSoc.shutdownOutput();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (mClientSoc != null) {
                try {
                    mClientSoc.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

//            mConnected = false;
            mConnectionStatus = DISCONNECTED;
            mOnConnectListener.onConnectStateChanged(false);
        }

        public boolean isConnected() {
            return mConnectionStatus == CONNECTED && !TextUtils.isEmpty(mServerIp);
        }
    }
}
