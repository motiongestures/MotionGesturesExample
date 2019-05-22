package com.motiongestures.grelib;

import android.content.Context;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import GREProtocol.Greapi;

public class RemoteGestureRecognitionClient extends AbstractGestureRecognitionClient {
    private static final String TAG = "RemoteGestureRecognitionClient";
    private SocketAdapter socketAdapter = new SocketAdapter();

    private WebSocket webSocket;
    private String currentSessionId = null;

    public RemoteGestureRecognitionClient(Context context) {
        super(context);
    }

    @Override
    public void pause() {
        unregisterSensorListeners();
        if(webSocket != null) {
            webSocket.removeListener(socketAdapter);
            webSocket.disconnect();
        }
    }

    @Override
    public void resume() {
        registerSensorListeners();
    }

    @Override
    public void disconnect() {
        super.sendLastSamples();
        webSocket.sendClose();
    }

    public void connect(String uri) {
        super.resetSamplesList();
        try {
            webSocket = new WebSocketFactory().createSocket(uri);
            webSocket.addListener(socketAdapter);
            currentSessionId = UUID.randomUUID().toString();
            webSocket.connectAsynchronously();
        } catch (IOException e) {
            Log.e(TAG, "Cannot create socket connection", e);
        }
    }

    protected Greapi.ReferenceMode convertToProtobufReferenceMode(ClientReferenceMode mode) {
        switch(mode) {
            case DEVICE_REFERENCE:
                return Greapi.ReferenceMode.DEVICE_REFERENCE;
            case USER_FACING:
                return Greapi.ReferenceMode.USER_FACING;
            case LEFT_WRIST:
                return Greapi.ReferenceMode.LEFT_WRIST;
            case RIGHT_WRIST:
                return Greapi.ReferenceMode.RIGHT_WRIST;
            default:
                return Greapi.ReferenceMode.DEVICE_REFERENCE;
        }
    }

    private Iterable<? extends Greapi.SensorSample> convertToProtobufSamples(Iterable<? extends SensorSample> samples) {
        List<Greapi.SensorSample> protobufSamples = new ArrayList<>();
        for(SensorSample sensorSample : samples) {
            protobufSamples.add(Greapi.SensorSample.newBuilder()
                    .setX(sensorSample.getX())
                    .setY(sensorSample.getY())
                    .setZ(sensorSample.getZ())
                    .setIndex(sensorSample.getIndex())
                    .build());
        }
        return protobufSamples;
    }

    @Override
    protected void sendSamples(Iterable<? extends SensorSample> accelerations,
                             Iterable<? extends SensorSample> gyroscope,
                             Iterable<? extends SensorSample> magnetometer) throws IOException {
        Greapi.Acceleration accelerationMessage = Greapi.Acceleration.newBuilder()
                .addAllSamples(convertToProtobufSamples(accelerations))
                .setUnit(Greapi.AccelerationUnit.SI)
                .build();
        Greapi.Gyroscope gyroscopeMessage = Greapi.Gyroscope.newBuilder()
                .addAllSamples(convertToProtobufSamples(gyroscope))
                .setUnit(Greapi.GyroscopeUnit.RADS)
                .build();
        Greapi.Magnetometer magnetometerMessage = Greapi.Magnetometer.newBuilder()
                .addAllSamples(convertToProtobufSamples(magnetometer))
                .build();
        Greapi.RecognitionRequest recognition = Greapi.RecognitionRequest.newBuilder()
                .setId(currentSessionId)
                .setSensitivity(150)
                .setActiveGesture(activeGesture)
                .setReferenceMode(convertToProtobufReferenceMode(referenceMode))
                .setAcceleration(accelerationMessage)
                .setGyroscope(gyroscopeMessage)
                .setMagnetometer(magnetometerMessage)
                .build();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        recognition.writeTo(outputStream);
        webSocket.sendBinary(outputStream.toByteArray());
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
            RemoteGestureRecognitionClient.super.sendCachedSamples();
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
