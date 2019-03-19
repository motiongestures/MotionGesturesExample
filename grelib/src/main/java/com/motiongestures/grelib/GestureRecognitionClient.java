package com.motiongestures.grelib;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

import GREProtocol.Greapi;

public class GestureRecognitionClient implements SensorEventListener {
    private static final String TAG = "GestureRecognitionClient";
    private SocketAdapter socketAdapter = new SocketAdapter();
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private static final int ACCELEROMETER_TYPE = Sensor.TYPE_ACCELEROMETER;
    private static final int GYROSCOPE_TYPE = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
    private static final int MAGNETOMETER_TYPE = Sensor.TYPE_MAGNETIC_FIELD;
    private static final int GYROSCOPE_TYPE_ALT = Sensor.TYPE_GYROSCOPE;
    private static final int MAGNETOMETER_TYPE_ALT = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED;

    private int maxSampleCachesize = 30;
    private Deque<Greapi.SensorSample> accelerationSamplesCache = new ConcurrentLinkedDeque<>();
    private Deque<Greapi.SensorSample> gyroscopeSamplesCache = new ConcurrentLinkedDeque<>();
    private Deque<Greapi.SensorSample> magnetometerSamplesCache = new ConcurrentLinkedDeque<>();
    private boolean activeGesture = false;

    private Collection<Greapi.SensorSample> accelerationList = new ConcurrentLinkedDeque<>();
    private Collection<Greapi.SensorSample> gyroscopeList  = new ConcurrentLinkedDeque<>();
    private Collection<Greapi.SensorSample> magnetometerList  = new ConcurrentLinkedDeque<>();
    private int index = 0;
    private WebSocket webSocket;
    private String currentSessionId = null;
    private GestureRecognitionResponseListener gestureRecognitionResponseListener;

    public GestureRecognitionClient(Context context) {

        sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(ACCELEROMETER_TYPE);
        gyroscope = sensorManager.getDefaultSensor(GYROSCOPE_TYPE);
        if(gyroscope == null) {
            Log.d(TAG,"Trying alt gyroscope: ");
            gyroscope = sensorManager.getDefaultSensor(GYROSCOPE_TYPE_ALT);
            if(gyroscope == null) {
                Log.e(TAG,"Cannot find gyroscope");
            }
        }

        magnetometer = sensorManager.getDefaultSensor(MAGNETOMETER_TYPE);
        if(magnetometer == null) {
            Log.d(TAG,"Trying alt magnetometer: ");
            magnetometer = sensorManager.getDefaultSensor(MAGNETOMETER_TYPE_ALT);
            if(magnetometer == null) {
                Log.e(TAG,"Cannot find magnetometer");
            }
        }
    }

    public void pause() {
        sensorManager.unregisterListener(this,accelerometer);
        sensorManager.unregisterListener(this,gyroscope);
        sensorManager.unregisterListener(this,magnetometer);
        if(webSocket != null) {
            webSocket.removeListener(socketAdapter);
            webSocket.disconnect();
        }
    }

    public void resume() {
        sensorManager.registerListener(this, accelerometer, 10_000);
        sensorManager.registerListener(this, gyroscope, 10_000);
        sensorManager.registerListener(this, magnetometer, 10_000);
    }

    public void disconnect() {
        //send the last samples
        activeGesture = false;
        try {
            sendSamples(accelerationList,gyroscopeList,magnetometerList);
        } catch (IOException e) {
            e.printStackTrace();
        }
        webSocket.sendClose();
    }

    public void connect(String uri) {
        index = 0;
        try {
            webSocket = new WebSocketFactory().createSocket(uri);
            webSocket.addListener(socketAdapter);
            currentSessionId = UUID.randomUUID().toString();
            webSocket.connectAsynchronously();
        } catch (IOException e) {
            Log.e(TAG, "Cannot create socket connection", e);
        }
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
                .setSensitivity(150)
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public GestureRecognitionResponseListener getGestureRecognitionResponseListener() {
        return gestureRecognitionResponseListener;
    }

    public void setGestureRecognitionResponseListener(GestureRecognitionResponseListener gestureRecognitionResponseListener) {
        this.gestureRecognitionResponseListener = gestureRecognitionResponseListener;
    }

    private final class SocketAdapter extends WebSocketAdapter {
        @Override
        public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
            try{
                ByteArrayInputStream inputStream = new ByteArrayInputStream(binary);
                final Greapi.RecognitionResponse recognitionResponse = Greapi.RecognitionResponse.parseFrom(inputStream);
                if(recognitionResponse.getStatus() == Greapi.Status.GestureEnd) {
                    if(gestureRecognitionResponseListener != null) {
                        gestureRecognitionResponseListener.gesturesRecognized(recognitionResponse.getNamesList(),
                                recognitionResponse.getLabelsList(),recognitionResponse.getConfidence());
                    }
                } else if(recognitionResponse.getStatus() == Greapi.Status.GestureRejected) {
                    if(gestureRecognitionResponseListener!= null) {
                        gestureRecognitionResponseListener.gesturesRejected(recognitionResponse.getNamesList(),
                                recognitionResponse.getLabelsList(), recognitionResponse.getConfidence());
                    }

                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int size = Math.min(recognitionResponse.getNamesCount(),recognitionResponse.getLabelsCount());
                            for(int i = 0;i<size;i++) {
                                gesturesListAdapter.add("Rejected gesture " + recognitionResponse.getNames(i) + " with label " + recognitionResponse.getLabels(i));
                            }
                            toggleButton.setChecked(false);
                        }
                    });*/
                } else if(recognitionResponse.getStatus() == Greapi.Status.GestureTooLong) {
                    if(gestureRecognitionResponseListener!= null) {
                        gestureRecognitionResponseListener.gestureTooLong();
                    }
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
