package com.motiongestures.grelib;

import java.util.List;

public interface GestureRecognitionResponseListener {

    void gesturesRecognized(List<String> names, List<Integer> labels, float confidence);
    void gesturesRejected(List<String> names,List<Integer> labels,float confidence);
    void gestureTooLong();
}
