package com.ar5000.audio;

public class AudioPreset {
    public static final int NARROW = 0; public static final int STANDARD = 1;
    private final int id; private final String name; private final int lowCut, highCut, sampleRate, channels;
    private final boolean agc; private final boolean timestamps;

    private AudioPreset(int i, String n, int l, int h, int sr, int ch, boolean a, boolean t) { id=i; name=n; lowCut=l; highCut=h; sampleRate=sr; channels=ch; agc=a; timestamps=t; }
    public int getId() { return id; } public String getName() { return name; }
    public int getLowCut() { return lowCut; } public int getHighCut() { return highCut; }
    public int getSampleRate() { return sampleRate; } public int getChannels() { return channels; }
    public boolean isAgc() { return agc; } public boolean isTimestamps() { return timestamps; }

    public static AudioPreset narrow() { return new AudioPreset(NARROW, "Narrow 3kHz", 300, 3300, 8000, 1, true, true); }
    public static AudioPreset standard() { return new AudioPreset(STANDARD, "Standard Full", 20, 20000, 48000, 2, false, true); }
}