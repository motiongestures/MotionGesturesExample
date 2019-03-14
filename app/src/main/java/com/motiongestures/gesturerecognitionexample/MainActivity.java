package com.motiongestures.gesturerecognitionexample;

import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ToggleButton;

import com.motiongestures.grelib.GestureRecognitionClient;
import com.motiongestures.grelib.GestureRecognitionResponseListener;

import java.util.List;

import static android.Manifest.permission.INTERNET;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_INTERNET = 0;
    private ArrayAdapter<String> gesturesListAdapter = null;
    private ListView recognizedGesturesList;
    private ToggleButton toggleButton = null;

    private GestureRecognitionClient gestureRecognitionClient;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        gestureRecognitionClient = new GestureRecognitionClient(this);
        gesturesListAdapter = new ArrayAdapter<>(this,R.layout.gesture_item);
        recognizedGesturesList = findViewById(R.id.recognizedGesturesList);
        recognizedGesturesList.setAdapter(gesturesListAdapter);
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

        gestureRecognitionClient.setGestureRecognitionResponseListener(new GestureRecognitionResponseListener() {
            @Override
            public void gesturesRecognized(final List<String> names, final List<Integer> labels, float confidence) {
                runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            int size = Math.min(names.size(),labels.size());
                            for(int i =0;i<size;i++) {
                                gesturesListAdapter.add("Recognized gesture " + names.get(i) + " with label " + labels.get(i));
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
                            gesturesListAdapter.add("Rejected gesture " + names.get(i) + " with label " + labels.get(i));
                        }
                        toggleButton.setChecked(false);
                    }
                });
            }

            @Override
            public void gestureTooLong() {
                gesturesListAdapter.add("Gesture Too Long");
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
