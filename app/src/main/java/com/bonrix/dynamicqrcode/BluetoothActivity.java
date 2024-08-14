package com.bonrix.dynamicqrcode;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.StrictMode;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

public class BluetoothActivity extends AppCompatActivity implements View.OnClickListener, ServiceConnection, SerialListener {
    String TAG = "BluetoothActivity";
    Toolbar toolbar;
    ImageView backarrow;
    private Button   btnWelcome, btnSuccess, btnFail, btnPending;
    private TextView receiveText;
    static Activity activity;

    private enum Connected {False, Pending, True}

    private String deviceAddress;
    private SerialService service;
    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private boolean pendingNewline = false;
    private String newline = TextUtil.newline_crlf;
    byte[] imageBytes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth);
        Log.e(TAG, "===onCreate=====");
        initComponent();
    }

    private void initComponent() {
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            deviceAddress = extras.getString("device");
        }
        activity = this;
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll().build();
        StrictMode.setThreadPolicy(policy);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        backarrow = findViewById(R.id.backarrow);

        receiveText = findViewById(R.id.tv_bt_status);
        btnWelcome = findViewById(R.id.btnWelcome);
        btnSuccess = findViewById(R.id.btnSuccess);
        btnFail = findViewById(R.id.btnFail);
        btnPending = findViewById(R.id.btnPending);

        backarrow.setOnClickListener(this);
        btnWelcome.setOnClickListener(this);
        btnSuccess.setOnClickListener(this);
        btnFail.setOnClickListener(this);
        btnPending.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == backarrow) {
            finish();
        }

        if (view == btnWelcome) {
            if (deviceAddress.isEmpty()) {
                Toast.makeText(activity, "Please connect bluetooth device.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                imageBytes = Apputils.getBytesFromAsset(this, "welcome.jpeg");
                String writeString = "StartSendingFile " + System.currentTimeMillis() + ".jpeg " + imageBytes.length + "\n";
                send(writeString);

            } catch (Exception e) {
                Log.e("TAG", "Exception   " + e);
            }
        }
        if (view == btnSuccess) {
            if (deviceAddress.isEmpty()) {
                Toast.makeText(activity, "Please connect bluetooth device.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                imageBytes = Apputils.getBytesFromAsset(this, "success.jpeg");
                String writeString = "StartSendingFile " + System.currentTimeMillis() + ".jpeg " + imageBytes.length + "\n";
                send(writeString);

            } catch (Exception e) {
                Log.e("TAG", "Exception   " + e);
            }
        }
        if (view == btnFail) {
            if (deviceAddress.isEmpty()) {
                Toast.makeText(activity, "Please connect bluetooth device.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                imageBytes = Apputils.getBytesFromAsset(this, "fail.jpeg");
                String writeString = "StartSendingFile " + System.currentTimeMillis() + ".jpeg " + imageBytes.length + "\n";
                send(writeString);

            } catch (Exception e) {
                Log.e("TAG", "Exception   " + e);
            }
        }
        if (view == btnPending) {
            if (deviceAddress.isEmpty()) {
                Toast.makeText(activity, "Please connect bluetooth device.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                imageBytes = Apputils.getBytesFromAsset(this, "pending.jpeg");
                String writeString = "StartSendingFile " + System.currentTimeMillis() + ".jpeg " + imageBytes.length + "\n";
                send(writeString);

            } catch (Exception e) {
                Log.e("TAG", "Exception   " + e);
            }
        }
    }

    private void send(String str) {
        Log.e(TAG,"str   "+str);
        if (connected != Connected.True) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            if (hexEnabled) {
                StringBuilder sb = new StringBuilder();
                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
                TextUtil.toHexString(sb, newline.getBytes());
                msg = sb.toString();
                data = TextUtil.fromHexString(msg);
            } else {
                msg = str;
                data = (str + newline).getBytes();
            }
            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }
    private void send1(byte[] result) {
        if (connected != Connected.True) {
            Toast.makeText(this, "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            service.write(result);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void receive(ArrayDeque<byte[]> datas) {
        SpannableStringBuilder spn = new SpannableStringBuilder();
        for (byte[] data : datas) {
            String msg = new String(data);
            if (msg.contains("Command")) {
                try {
                    int chunkSize = 2048; // Example chunk size of 1KB
                    int intervalMillis = 10; // Interval in milliseconds between each chunk
                    int totalChunks = (int) Math.ceil((double) imageBytes.length / chunkSize);
                    Log.e("TAG","totalChunks"+totalChunks);
                    final int[] currentIndex = {0};
                    Timer timer = new Timer();
                    timer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            Log.e("TAG","currentIndex "+currentIndex[0]);
                            if (currentIndex[0] < totalChunks) {
                                int startIndex = currentIndex[0] * chunkSize;
                                Log.e("TAG","startIndex "+startIndex);
                                int endIndex = Math.min(startIndex + chunkSize, imageBytes.length);
                                byte[] chunk = Arrays.copyOfRange(imageBytes, startIndex, endIndex);
                                send1(chunk);
                                currentIndex[0]++;
                            } else {
                                // All chunks sent, cancel the timer
                                timer.cancel();
                            }
                        }
                    }, 0, intervalMillis);
                } catch (Exception e) {

                }
            }


        }
    }

    private void receive1(byte[] data) {
        String msg = new String(data);
        Log.e(TAG,"receive  "+msg);
        if (msg.contains("Command")) {
            try {
                int chunkSize = 2048; // Example chunk size of 1KB
                int intervalMillis = 10; // Interval in milliseconds between each chunk
                int totalChunks = (int) Math.ceil((double) imageBytes.length / chunkSize);
                Log.e("TAG","totalChunks"+totalChunks);
                final int[] currentIndex = {0};
                Timer timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.e("TAG","currentIndex "+currentIndex[0]);
                        if (currentIndex[0] < totalChunks) {
                            int startIndex = currentIndex[0] * chunkSize;
                            Log.e("TAG","startIndex "+startIndex);
                            int endIndex = Math.min(startIndex + chunkSize, imageBytes.length);
                            byte[] chunk = Arrays.copyOfRange(imageBytes, startIndex, endIndex);
                            send1(chunk);
                            currentIndex[0]++;
                        } else {
                            // All chunks sent, cancel the timer
                            timer.cancel();
                        }
                    }
                }, 0, intervalMillis);
            } catch (Exception e) {

            }
        }

    }



    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        stopService(new Intent(this, SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();


        Log.e(TAG, "===onStart=====");
        if (service != null) {
            Log.e(TAG, "service not null");
            service.attach(this);
        } else {
            Log.e(TAG, "service  null");
            bindService(new Intent(this, SerialService.class), this, Context.BIND_AUTO_CREATE);

            startService(new Intent(this, SerialService.class));
        }
    }

    @Override
    public void onStop() {
        if (service != null && !isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (initialStart && service != null) {
            initialStart = false;
            runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.e(TAG, "onServiceConnected");
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if (initialStart) {
            initialStart = false;
            runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.e(TAG, "onServiceDisconnected");

        service = null;
    }

    private void connect() {
        Log.e(TAG, "connect  " + deviceAddress);
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            Log.e(TAG, "Exceptionnnn  " + e);
            onSerialConnectError(e);
        }
    }




    private void status(String str) {
        receiveText.setText("");
        Log.e(TAG, "status   " + str);
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        receiveText.append(spn);
    }

    @Override
    public void onSerialConnect() {
        Log.e(TAG, "onSerialConnect");
        status("connected");
        connected = Connected.True;
    }

    @Override
    public void onSerialConnectError(Exception e) {
        Log.e(TAG, "onSerialConnectError   " + e);
        status("connection failed: ");
        disconnect();

        if (connected==Connected.False){
            runOnUiThread(this::connect);
        }
    }

    @Override
    public void onSerialRead(byte[] data) {
        Log.e(TAG, "onSerialRead   " );
        receive1(data);
    }

    public void onSerialRead(ArrayDeque<byte[]> datas) {
        Log.e(TAG, "onSerialRead 1  " );
        receive(datas);
    }

    @Override
    public void onSerialIoError(Exception e) {
        Log.e(TAG, "onSerialIoError");
        status("connection lost: " + e.getMessage());
        disconnect();
        if (connected==Connected.False){
            runOnUiThread(this::connect);
        }
    }

    private void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }


}
