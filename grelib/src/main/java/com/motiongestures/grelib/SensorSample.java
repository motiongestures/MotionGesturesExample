package com.motiongestures.grelib;

import java.util.Objects;

public class SensorSample {
    private float x;
    private float y;
    private float z;
    private int index;

    public SensorSample(float x, float y, float z, int index) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.index = index;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public int getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorSample that = (SensorSample) o;
        return Float.compare(that.x, x) == 0 &&
                Float.compare(that.y, y) == 0 &&
                Float.compare(that.z, z) == 0 &&
                index == that.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z, index);
    }
}
