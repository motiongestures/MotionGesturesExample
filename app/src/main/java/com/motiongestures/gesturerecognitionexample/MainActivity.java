package com.motiongestures.gesturerecognitionexample;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ToggleButton;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import GREProtocol.Greapi;

import static android.Manifest.permission.INTERNET;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_INTERNET = 0;
    private ArrayAdapter<String> gesturesListAdapter = null;
    private ListView recognizedGesturesList;
    private ToggleButton toggleButton = null;

    private SocketAdapter socketAdapter = new SocketAdapter();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private static final int ACCELEROMETER_TYPE = Sensor.TYPE_ACCELEROMETER;
    private static final int GYROSCOPE_TYPE = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
    private static final int MAGNETOMETER_TYPE = Sensor.TYPE_MAGNETIC_FIELD;
    private int maxSampleCachesize = 30;
    private Deque<Greapi.SensorSample> accelerationSamplesCache = new LinkedList<>();
    private Deque<Greapi.SensorSample> gyroscopeSamplesCache = new LinkedList<>();
    private Deque<Greapi.SensorSample> magnetometerSamplesCache = new LinkedList<>();
    private boolean activeGesture = false;

    private List<Greapi.SensorSample> accelerationList = new ArrayList<>();
    private List<Greapi.SensorSample> gyroscopeList  = new ArrayList<>();
    private List<Greapi.SensorSample> magnetometerList  = new ArrayList<>();
    private int index = 0;
    private WebSocket webSocket;
    private String currentSessionId = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        gesturesListAdapter = new ArrayAdapter<>(this,R.layout.gesture_item);
        recognizedGesturesList = findViewById(R.id.recognizedGesturesList);
        recognizedGesturesList.setAdapter(gesturesListAdapter);
        toggleButton = findViewById(R.id.test_toggle);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if(checked) {
                    //start
                    connect();
                } else {
                    //stop
                    disconnect();
                }
            }
        });
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(ACCELEROMETER_TYPE);
        gyroscope = sensorManager.getDefaultSensor(GYROSCOPE_TYPE);
        magnetometer = sensorManager.getDefaultSensor(MAGNETOMETER_TYPE);
        boolean canConnect = mayConnectToInternet();
        toggleButton.setEnabled(canConnect);
    }

    private void disconnect() {
        //send the last samples
        activeGesture = false;
        try {
            sendSamples(accelerationList,gyroscopeList,magnetometerList);
        } catch (IOException e) {
            e.printStackTrace();
        }
        webSocket.sendClose();
    }

    private void connect() {
        index = 0;
        try {
            webSocket = new WebSocketFactory().createSocket("wss://sdk.motiongestures.com/recognition?api_key=<replace key>");
            webSocket.addListener(socketAdapter);
            currentSessionId = UUID.randomUUID().toString();
            webSocket.connectAsynchronously();
        } catch (IOException e) {
            Log.e(TAG, "Cannot create socket connection", e);
        }
    }

    @Override
    protected void onPause() {
        toggleButton.setChecked(false);
        sensorManager.unregisterListener(this,accelerometer);
        sensorManager.unregisterListener(this,gyroscope);
        sensorManager.unregisterListener(this,magnetometer);
        if(webSocket != null) {
            webSocket.removeListener(socketAdapter);
            webSocket.disconnect();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        sensorManager.registerListener(MainActivity.this, accelerometer, 10_000);
        sensorManager.registerListener(MainActivity.this, gyroscope, 10_000);
        sensorManager.registerListener(MainActivity.this, magnetometer, 10_000);
        super.onResume();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        Greapi.SensorSample sample = Greapi.SensorSample.newBuilder().setX(x).setY(y).setZ(z).setIndex(index).build();
        switch(sensorEvent.sensor.getType())
        {
            case ACCELEROMETER_TYPE:
                if(activeGesture) {
                    accelerationList.add(sample);
                }
                addSampleToCache(accelerationSamplesCache,sample);
                break;
            case GYROSCOPE_TYPE:
                if(activeGesture) {
                    gyroscopeList.add(sample);
                }
                addSampleToCache(gyroscopeSamplesCache,sample);
                break;
            case MAGNETOMETER_TYPE:
                if(activeGesture) {
                    magnetometerList.add(sample);
                }
                addSampleToCache(magnetometerSamplesCache,sample);
                break;
        }
        index++;
        if(shouldSendData()) {
            try {
                sendSamples(accelerationList,gyroscopeList,magnetometerList);
                accelerationList.clear();
                gyroscopeList.clear();
                magnetometerList.clear();
            } catch (IOException ex) {
                Log.e(TAG, "Error sending acceleration data to the server", ex);
            }
        }
    }

    private void sendSamples(Iterable<? extends Greapi.SensorSample> accelerations,
                                              Iterable<? extends Greapi.SensorSample> gyroscope,
                                              Iterable<? extends Greapi.SensorSample> magnetometer) throws IOException {
        Greapi.Acceleration accelerationMessage = Greapi.Acceleration.newBuilder()
                .addAllSamples(accelerations)
                .setUnit(Greapi.AccelerationUnit.SI)
                .build();
        Greapi.Gyroscope gyroscopeMessage = Greapi.Gyroscope.newBuilder()
                .addAllSamples(gyroscope)
                .setUnit(Greapi.GyroscopeUnit.RADS)
                .build();
        Greapi.Magnetometer magnetometerMessage = Greapi.Magnetometer.newBuilder()
                .addAllSamples(magnetometer)
                .build();
        Greapi.RecognitionRequest recognition = Greapi.RecognitionRequest.newBuilder()
                .setId(currentSessionId)
                .setSensitivity(100)
                .setActiveGesture(activeGesture)
                .setAcceleration(accelerationMessage)
                .setGyroscope(gyroscopeMessage)
                .setMagnetometer(magnetometerMessage)
                .build();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        recognition.writeTo(outputStream);
        webSocket.sendBinary(outputStream.toByteArray());
    }

    private void addSampleToCache(Deque<Greapi.SensorSample> samplesCache, Greapi.SensorSample sensorSample) {
        samplesCache.addLast(sensorSample);
        while(samplesCache.size() > maxSampleCachesize) {
            samplesCache.removeFirst();
        }
    }

    private boolean shouldSendData() {
        return accelerationList.size() >= 100 || gyroscopeList.size() >= 100 || magnetometerList.size() >= 100;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //ignored
    }

    private boolean mayConnectToInternet() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Log.d(TAG,"We can connect to the internet");
            return true;
        }
        if (checkSelfPermission(INTERNET) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG,"We can connect to the internet");
            return true;
        }
        requestPermissions(new String[]{INTERNET}, REQUEST_INTERNET);
        Log.d(TAG,"Cannot connect to the internet");
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_INTERNET) {
            toggleButton.setEnabled(grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED);
        }
    }

    private final class SocketAdapter extends WebSocketAdapter {
        @Override
        public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            try{
                ByteArrayInputStream inputStream = new ByteArrayInputStream(binary);
                final Greapi.RecognitionResponse recognitionResponse = Greapi.RecognitionResponse.parseFrom(inputStream);
                if(recognitionResponse.getStatus() == Greapi.Status.GestureEnd) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int size = Math.min(recognitionResponse.getNamesCount(),recognitionResponse.getLabelsCount());
                            for(int i =0;i<size;i++) {
                                gesturesListAdapter.add("Recognized gesture " + recognitionResponse.getNames(i) + " with label " + recognitionResponse.getLabels(i));
                            }
                            toggleButton.setChecked(false);
                        }
                    });
                } else {
                    Log.d(TAG,"Received recognition response with status "+recognitionResponse.getStatus());
                }
            }catch(IOException ex) {
                Log.e(TAG,"Error deserializing the recognition response",ex);
            }
        }
        @Override
        public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
            Log.d(TAG,"Connected to server");
            activeGesture = false;
            sendSamples(accelerationSamplesCache,gyroscopeSamplesCache,magnetometerSamplesCache);
            activeGesture = true;
        }

        @Override
        public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
            super.onError(websocket, cause);
            Log.e(TAG,"Received an error communicating with the server:",cause);
        }

        @Override
        public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer);
            int code = closedByServer?serverCloseFrame.getCloseCode():clientCloseFrame.getCloseCode();
            Log.e(TAG,"Disconnected from server with code "+code);
        }
    }

}
