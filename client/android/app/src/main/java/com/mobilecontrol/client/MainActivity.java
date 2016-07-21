package com.mobilecontrol.client;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.TextView;

import com.mobilecontrol.client.data.TouchData;
import com.mobilecontrol.client.net.MobileControlClient;
import com.mobilecontrol.client.net.MobileControlClient.OnConnectListener;

public class MainActivity extends Activity implements View.OnClickListener {

    protected static final String TAG = "MainActivity";

    private long mLongClickTime = 400;
    private int mSpeed = 1;
    private MobileControlClient mControlClient;
    private TextView mConnectionStatusView;

    private OnConnectListener mOnConnectListener = new OnConnectListener() {

        @Override
        public void onDisconnected(final boolean disconnectedByServer) {
            Log.d(TAG, "onDisconnected" + (disconnectedByServer ? " by server" : ""));
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mConnectionStatusView.setText(disconnectedByServer
                            ? R.string.disconnected_by_server : R.string.disconnected);
                }
            });
        }

        @Override
        public void onFindServerComplete(final boolean found) {
            MainActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    mConnectionStatusView.setText(found ? R.string.connected : R.string.server_not_found);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_left).setOnClickListener(this);
        findViewById(R.id.btn_right).setOnClickListener(this);

        findViewById(R.id.touch_pad).setOnTouchListener(new OnTouchListener() {

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
//                        td_c.setX((int) x);
//                        td_c.setY((int) y);
                            send(td_c);
                        }
                        break;
                }
                return true;
            }
        });

        mConnectionStatusView = (TextView) findViewById(R.id.connection_status);

        MobileControlApp app = (MobileControlApp) getApplication();
        mControlClient = app.getMobileControlClient();
        if (mControlClient == null) {
            mControlClient = new MobileControlClient();
            mControlClient.setOnConnectListener(mOnConnectListener);
            if (!mControlClient.isConnected()) {
                mControlClient.findServer();
            }

            app.setMobileControlClient(mControlClient);
        }

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
        mControlClient.findServer();
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_left:
                onLeftClicked();
                break;
            case R.id.btn_right:
                onRightClicked();
                break;
        }
    }

    private void onRightClicked() {
        TouchData td_c = new TouchData();
        td_c.setType(TouchData.TOUCH_TYPE_LONG_CLICK);
        send(td_c);
    }

    private void onLeftClicked() {
        TouchData td_c = new TouchData();
        td_c.setType(TouchData.TOUCH_TYPE_CLICK);
        send(td_c);
    }
}
