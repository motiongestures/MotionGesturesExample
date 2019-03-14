package com.motiongestures.wearosgesturesexample;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.motiongestures.grelib.GestureRecognitionClient;
import com.motiongestures.grelib.GestureRecognitionResponseListener;
import java.util.List;

import static android.Manifest.permission.INTERNET;

public class WearMainActivity extends WearableActivity {
    private static final String TAG = "WearMainActivity";
    private static final int REQUEST_INTERNET = 0;

    private TextView mTextView;
    private ToggleButton toggleButton;
    private GestureRecognitionClient gestureRecognitionClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wear_main);
        gestureRecognitionClient = new GestureRecognitionClient(this);
        mTextView = (TextView) findViewById(R.id.text);
        toggleButton = findViewById(R.id.test_toggle);
        toggleButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if(checked) {
                    //start
                    gestureRecognitionClient.connect("wss://sdk.motiongestures.com/recognition?api_key=<project key here>");
                } else {
                    //stop
                    gestureRecognitionClient.disconnect();
                }
            }
        });

        boolean canConnect = mayConnectToInternet();
        toggleButton.setEnabled(canConnect);
        // Enables Always-on
        setAmbientEnabled();

        gestureRecognitionClient.setGestureRecognitionResponseListener(new GestureRecognitionResponseListener() {
            @Override
            public void gesturesRecognized(final List<String> names, final List<Integer> labels, float confidence) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int size = Math.min(names.size(),labels.size());
                        for(int i =0;i<size;i++) {
                            mTextView.setText("Recognized gesture " + names.get(i) + " with label " + labels.get(i));
                        }
                        toggleButton.setChecked(false);
                    }
                });
            }

            @Override
            public void gesturesRejected(final List<String> names, final List<Integer> labels, float confidence) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int size = Math.min(names.size(),labels.size());
                        for(int i =0;i<size;i++) {
                            mTextView.setText("Rejected gesture " + names.get(i) + " with label " + labels.get(i));
                        }
                        toggleButton.setChecked(false);
                    }
                });
            }

            @Override
            public void gestureTooLong() {
                mTextView.setText("Gesture Too Long");
            }
        });
    }


    @Override
    protected void onPause() {
        gestureRecognitionClient.pause();
        toggleButton.setChecked(false);
        super.onPause();
    }

    @Override
    protected void onResume() {
        gestureRecognitionClient.resume();
        super.onResume();
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
}
