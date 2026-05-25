package com.ar5000.audio;

/**
 * Simple Goertzel-based DTMF tone detector.
 * Detects standard DTMF frequencies: 697/770/852/941 Hz (low) + 1209/1336/1477/1633 Hz (high).
 * All comments use ASCII-only characters.
 */
public class DtmfDetector {

    // DTMF frequency pairs [low][high] -> digit
    private static final char[][] DTMF_MAP = {
            {'1', '2', '3', 'A'},  // 697 Hz
            {'4', '5', '6', 'B'},  // 770 Hz
            {'7', '8', '9', 'C'},  // 852 Hz
            {'*', '0', '#', 'D'}   // 941 Hz
    };

    private static final int[] LOW_FREQS = {697, 770, 852, 941};
    private static final int[] HIGH_FREQS = {1209, 1336, 1477, 1633};
    private static final int SAMPLE_RATE = 8000;  // Downsampled for detection
    private static final int BLOCK_SIZE = 205;    // Goertzel block size
    private static final float THRESHOLD = 0.25f; // Detection threshold

    private float[] lowPowers = new float[4];
    private float[] highPowers = new float[4];

    /**
     * Process PCM block and return detected digit or 0 if none.
     * @param pcm 16-bit signed PCM samples (mono, 8kHz recommended)
     * @param offset Start offset
     * @param length Number of samples to process
     * @return Detected DTMF digit or 0
     */
    public char detect(short[] pcm, int offset, int length) {
        if (length < BLOCK_SIZE) return 0;

        // Calculate Goertzel power for each frequency
        for (int i = 0; i < 4; i++) {
            lowPowers[i] = goertzelPower(pcm, offset, length, LOW_FREQS[i]);
            highPowers[i] = goertzelPower(pcm, offset, length, HIGH_FREQS[i]);
        }

        // Find strongest low and high frequencies
        int lowIdx = maxIndex(lowPowers);
        int highIdx = maxIndex(highPowers);

        // Check if both exceed threshold
        if (lowPowers[lowIdx] > THRESHOLD && highPowers[highIdx] > THRESHOLD) {
            return DTMF_MAP[lowIdx][highIdx];
        }
        return 0;
    }

    // Goertzel algorithm for single frequency power estimation
    private float goertzelPower(short[] samples, int offset, int length, int freq) {
        float omega = (float) (2.0 * Math.PI * freq / SAMPLE_RATE);
        float coeff = 2.0f * (float) Math.cos(omega);

        float sPrev = 0f, sPrev2 = 0f;
        int n = Math.min(length, BLOCK_SIZE);

        for (int i = 0; i < n; i++) {
            float s = (samples[offset + i] / 32768f) + coeff * sPrev - sPrev2;
            sPrev2 = sPrev;
            sPrev = s;
        }

        // Power calculation
        return sPrev * sPrev + sPrev2 * sPrev2 - coeff * sPrev * sPrev2;
    }

    private int maxIndex(float[] arr) {
        int maxIdx = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[maxIdx]) maxIdx = i;
        }
        return maxIdx;
    }

    // Callback interface for detected digits
    public interface DtmfListener {
        void onDigitDetected(char digit, long timestampMs);
    }
}