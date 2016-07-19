package com.mobilecontrol.client;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.mobilecontrol.client.data.TouchData;
import com.mobilecontrol.client.net.MobileControlClient;
import com.mobilecontrol.client.net.MobileControlClient.OnConnectListener;

public class MainActivity extends Activity {

    protected static final String TAG = "MainActivity";

    private long mLongClickTime = 400;
    private int mSpeed = 1;
    private MobileControlClient mControlClient;
    private LinearLayout mConnectingSpinner;
    private TextView mDisConnectedMsg;

    private OnConnectListener mOnConnectListener = new OnConnectListener() {

        @Override
        public void onConnectStateChanged(final boolean connected) {
            Log.d(TAG, "onConnectStateChanged: "
                    + (connected ? "connected" : "disconnected"));
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mConnectingSpinner.setVisibility(View.GONE);
                    mDisConnectedMsg.setVisibility(connected ? View.GONE
                            : View.VISIBLE);
                }
            });
        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RelativeLayout root = (RelativeLayout) LayoutInflater.from(
                getApplicationContext()).inflate(R.layout.activity_main, null);
        setContentView(root);
        root.setOnTouchListener(new OnTouchListener() {

            float lastX, lastY, downX, downY;
            long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = lastX = event.getX();
                    downY = lastY = event.getY();
                    downTime = System.currentTimeMillis();
                    break;
                case MotionEvent.ACTION_MOVE:
                    float x = event.getX();
                    float y = event.getY();
                    float dx = x - lastX;
                    float dy = y - lastY;
                    lastX = x;
                    lastY = y;

                    TouchData td = new TouchData();
                    td.setType(TouchData.TOUCH_TYPE_MOVE);
                    td.setX(mSpeed * (int) dx);
                    td.setY(mSpeed * (int) dy);
                    send(td);
                    break;
                case MotionEvent.ACTION_UP:
                    x = event.getX();
                    y = event.getY();
                    dx = x - downX;
                    dy = y - downY;
                    if (dx < 2 && dy < 2) {
                        // this is a click event
                        TouchData td_c = new TouchData();
                        long tx = System.currentTimeMillis() - downTime;
                        Log.d(TAG, "tx " + tx);
                        td_c.setType(tx > mLongClickTime ? TouchData.TOUCH_TYPE_LONG_CLICK
                                : TouchData.TOUCH_TYPE_CLICK);
                        td_c.setX((int) x);
                        td_c.setY((int) y);
                        send(td_c);
                    }
                    break;
                }
                return true;
            }
        });

        mConnectingSpinner = (LinearLayout) findViewById(R.id.connecting_spinner);
        mDisConnectedMsg = (TextView) findViewById(R.id.disconnected);

        MobileControlApp app = (MobileControlApp) getApplication();
        mControlClient = app.getMobileControlClient();
        if (mControlClient == null) {
            mControlClient = new MobileControlClient();
            mControlClient.setOnConnectListener(mOnConnectListener);
            if (!mControlClient.isConnected()) {
                mControlClient.connect();
            }

            app.setMobileControlClient(mControlClient);
        }

        mConnectingSpinner
                .setVisibility(mControlClient.isConnected() ? View.GONE
                        : View.VISIBLE);

//        mConnectingSpinner.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_connect:
            connect();
            return true;
        case R.id.action_send:
            sendHello();
            return true;
        case R.id.action_disconnect:
            disconnect();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // mControlClient.disconnect();
    }

    private void connect() {
        mControlClient.connect();
    }

    private void disconnect() {
        mControlClient.disconnect();
    }

    private void sendHello() {
        mControlClient.send("Hello");
    }

    private void send(TouchData td) {
        if (td == null || !mControlClient.isConnected()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(td.getHead()).append(",")
                .append(td.getType()).append(",")
                .append(td.getX()).append(",")
                .append(td.getY());
        String jsonStr = sb.toString();
        Log.d(TAG, "send: " + jsonStr);
        mControlClient.send(jsonStr);
    }
}
