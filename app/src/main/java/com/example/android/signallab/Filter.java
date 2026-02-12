package com.example.android.signallab;

public class Filter {
    private double a0,a1,a2, b0, b1,b2;
    private double z1 = 0, z2 = 0;
    public enum Type {
        LOWPASS,
        BANDPASS,
        HIGHPASS
    }
    public Filter(double SampleRate, double freq, double q, Type type){
        setCoefficients(
                SampleRate,
                freq,
                q,
                type
        );
    }
    private void setCoefficients(double SampleRate, double freq, double q, Type type){
        double omega = 2 * Math.PI * freq / SampleRate;
        double alpha = Math.sin(omega) / (2*q);
        double cosw = Math.cos(omega);

        switch(type) {
            case LOWPASS:
                b0 = (1-cosw)/2;
                b1 = 1-cosw;
                b2 = (1-cosw)/2;
                a0 = 1 + alpha;
                a1 = -2*cosw;
                a2 = 1 - alpha;
                break;
            case BANDPASS:
                b0 = alpha;
                b1 = 0;
                b2 = -alpha;
                a0 = 1 + alpha;
                a1 = -2*cosw;
                a2 = 1 - alpha;
                break;
            case HIGHPASS:
                b0 = (1 + cosw)/2;
                b1 = -(1 + cosw);
                b2 = (1 + cosw)/2;
                a0 = 1 + alpha;
                a1 = -2*cosw;
                a2 = 1 - alpha;
        }
    }
    public float process(float x) {
        double out = (b0 / a0) * x + z1;
        z1 = (b1 / a0) * x - (a1 / a0) * out + z2;
        z2 = (b2 / a0) * x - (a2 / a0) * out;
        return (float)  out;
    }
    public void reset(){
        z1 = 0;
        z2 = 0;
    }
}
