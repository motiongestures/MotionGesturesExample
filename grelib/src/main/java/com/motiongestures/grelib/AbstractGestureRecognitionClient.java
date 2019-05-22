package com.motiongestures.grelib;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public abstract class AbstractGestureRecognitionClient implements GestureRecognitionClient, SensorEventListener {
    private static final String TAG = "AbstractGestureRecognitionClient";

    private static final int ACCELEROMETER_TYPE = Sensor.TYPE_ACCELEROMETER;
    private static final int GYROSCOPE_TYPE = Sensor.TYPE_GYROSCOPE_UNCALIBRATED;
    private static final int MAGNETOMETER_TYPE = Sensor.TYPE_MAGNETIC_FIELD;
    private static final int GYROSCOPE_TYPE_ALT = Sensor.TYPE_GYROSCOPE;
    private static final int MAGNETOMETER_TYPE_ALT = Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    protected boolean activeGesture = false;

    private Collection<SensorSample> accelerationList = new ConcurrentLinkedDeque<>();
    private Collection<SensorSample> gyroscopeList  = new ConcurrentLinkedDeque<>();
    private Collection<SensorSample> magnetometerList  = new ConcurrentLinkedDeque<>();
    private Deque<SensorSample> accelerationSamplesCache = new ConcurrentLinkedDeque<>();
    private Deque<SensorSample> gyroscopeSamplesCache = new ConcurrentLinkedDeque<>();
    private Deque<SensorSample> magnetometerSamplesCache = new ConcurrentLinkedDeque<>();

    private int index = 0;
    private int maxSampleCacheSize = 30;

    protected GestureRecognitionResponseListener gestureRecognitionResponseListener;
    protected ClientReferenceMode referenceMode = ClientReferenceMode.DEVICE_REFERENCE;


    public AbstractGestureRecognitionClient(Context context) {
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

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];
        SensorSample sample = new SensorSample(x,y,z,index);
        switch(sensorEvent.sensor.getType())
        {
            case ACCELEROMETER_TYPE:
                if(activeGesture) {
                    accelerationList.add(sample);
                }
                addSampleToCache(accelerationSamplesCache,sample);
                break;
            case GYROSCOPE_TYPE:
            case GYROSCOPE_TYPE_ALT:
                if(activeGesture) {
                    gyroscopeList.add(sample);
                }
                addSampleToCache(gyroscopeSamplesCache,sample);
                break;
            case MAGNETOMETER_TYPE:
            case MAGNETOMETER_TYPE_ALT:
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

    protected abstract void sendSamples(Iterable<? extends SensorSample> accelerations,
                               Iterable<? extends SensorSample> gyroscope,
                               Iterable<? extends SensorSample> magnetometer) throws IOException;

    private void addSampleToCache(Deque<SensorSample> samplesCache, SensorSample sensorSample) {
        samplesCache.addLast(sensorSample);
        while(samplesCache.size() > maxSampleCacheSize) {
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

    @Override
    public void setReferenceMode(ClientReferenceMode referenceMode) {
        this.referenceMode = referenceMode;
    }

    public void setGestureRecognitionResponseListener(GestureRecognitionResponseListener gestureRecognitionResponseListener) {
        this.gestureRecognitionResponseListener = gestureRecognitionResponseListener;
    }

    protected void resetSamplesList() {
        index = 0;
        accelerationList.clear();
        gyroscopeList.clear();
        magnetometerList.clear();
    }

    protected void sendLastSamples() {
        //send the last samples
        activeGesture = false;
        try {
            sendSamples(accelerationList,gyroscopeList,magnetometerList);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void sendCachedSamples() throws IOException {
        sendSamples(accelerationSamplesCache,gyroscopeSamplesCache,magnetometerSamplesCache);
    }

    protected void unregisterSensorListeners() {
        sensorManager.unregisterListener(this,accelerometer);
        sensorManager.unregisterListener(this,gyroscope);
        sensorManager.unregisterListener(this,magnetometer);
    }

    protected void registerSensorListeners() {
        sensorManager.registerListener(this, accelerometer, 10_000);
        sensorManager.registerListener(this, gyroscope, 10_000);
        sensorManager.registerListener(this, magnetometer, 10_000);
    }
}
