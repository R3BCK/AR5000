// SpectrumActivity.java
package com.ar5000.app;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.ar5000.audio.SpectrumView;
import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.transport.Transport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SpectrumActivity extends AppCompatActivity {

    private static final String TAG = "AR5000-SPECTRUM";

    // ===== UI =====
    private SpectrumView spectrumView;
    private EditText etCenter;
    private Spinner spSpan, spMode;
    private SeekBar sbGain;
    private TextView tvGainVal, tvStatus;
    private Button btnApply, btnSettings;

    // ===== AUDIO CAPTURE (CM108) =====
    private static final int SAMPLE_RATE = 48000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT) * 2;

    private AudioRecord audioRecord;
    private ExecutorService audioExecutor;
    private AtomicBoolean capturing = new AtomicBoolean(false);

    // ===== SPECTRUM SETTINGS =====
    private float centerFreqMhz = 145.0f;
    private float spanKhz = 24.0f;
    private boolean waterfallMode = true;
    private int gainFactor = 50; // 0-100, applied as multiplier to FFT magnitude

    // ===== CORE =====
    private Transport transport;
    private Ar5000Controller controller;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_spectrum);

        initCore();
        initViews();
        initAdapters();
        setupListeners();
        loadSavedSettings();
        initAudioCapture();
        startCapture();
    }

    private void initCore() {
        uiHandler = new Handler(Looper.getMainLooper());
        audioExecutor = Executors.newSingleThreadExecutor();
        transport = MainActivity.getTransport();
        controller = MainActivity.getController();
    }

    private void initViews() {
        spectrumView = findViewById(R.id.spectrumView);
        etCenter = findViewById(R.id.etCenter);
        spSpan = findViewById(R.id.spSpan);
        spMode = findViewById(R.id.spMode);
        sbGain = findViewById(R.id.sbGain);
        tvGainVal = findViewById(R.id.tvGainVal);
        tvStatus = findViewById(R.id.tvStatus);
        btnApply = findViewById(R.id.btnApply);
        btnSettings = findViewById(R.id.btnSettings);

        // Initialize spectrum view
        spectrumView.setCenter(centerFreqMhz * 1e6f);
        spectrumView.setSpan(spanKhz * 1e3f);
        spectrumView.setMode(waterfallMode);
    }

    private void initAdapters() {
        // Span presets in kHz
        String[] spans = {"12", "24", "48", "96", "192", "384", "768", "1536"};
        ArrayAdapter<String> spanAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, spans);
        spanAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSpan.setAdapter(spanAdapter);
        spSpan.setSelection(1); // 24 kHz default

        // Mode: Spectrum / Waterfall
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"Spectrum", "Waterfall"});
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMode.setAdapter(modeAdapter);
        spMode.setSelection(waterfallMode ? 1 : 0);
    }

    private void setupListeners() {
        // Apply settings
        btnApply.setOnClickListener(v -> {
            try {
                centerFreqMhz = Float.parseFloat(etCenter.getText().toString().trim());
                spanKhz = Float.parseFloat(spSpan.getSelectedItem().toString());
                waterfallMode = spMode.getSelectedItemPosition() == 1;
                gainFactor = sbGain.getProgress();

                // Apply to spectrum view
                spectrumView.setCenter(centerFreqMhz * 1e6f);
                spectrumView.setSpan(spanKhz * 1e3f);
                spectrumView.setMode(waterfallMode);

                // Save settings
                saveSavedSettings();

                tvStatus.setText("Applied: " + centerFreqMhz + " MHz ±" + (spanKhz/2) + " kHz");
                Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid center frequency", Toast.LENGTH_SHORT).show();
            }
        });

        // Gain seekbar
        sbGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int val, boolean from) {
                gainFactor = val;
                tvGainVal.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Settings button
        btnSettings.setOnClickListener(v -> {
            Toast.makeText(this, "CM108 audio settings: coming soon", Toast.LENGTH_SHORT).show();
        });

        // Auto-apply on span/mode change
        spSpan.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                spanKhz = Float.parseFloat(spSpan.getSelectedItem().toString());
                spectrumView.setSpan(spanKhz * 1e3f);
                saveSavedSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        spMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                waterfallMode = (pos == 1);
                spectrumView.setMode(waterfallMode);
                saveSavedSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void loadSavedSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("ar5000_spectrum_prefs", MODE_PRIVATE);
        centerFreqMhz = prefs.getFloat("centerFreq", 145.0f);
        spanKhz = prefs.getFloat("span", 24.0f);
        waterfallMode = prefs.getBoolean("waterfall", true);
        gainFactor = prefs.getInt("gain", 50);

        etCenter.setText(String.format("%.3f", centerFreqMhz));
        spSpan.setSelection(findSpanIndex((int)spanKhz));
        spMode.setSelection(waterfallMode ? 1 : 0);
        sbGain.setProgress(gainFactor);
        tvGainVal.setText(String.valueOf(gainFactor));
    }

    private void saveSavedSettings() {
        android.content.SharedPreferences.Editor e = getSharedPreferences("ar5000_spectrum_prefs", MODE_PRIVATE).edit();
        e.putFloat("centerFreq", centerFreqMhz);
        e.putFloat("span", spanKhz);
        e.putBoolean("waterfall", waterfallMode);
        e.putInt("gain", gainFactor);
        e.apply();
    }

    private int findSpanIndex(int spanKhz) {
        String[] spans = {"12", "24", "48", "96", "192", "384", "768", "1536"};
        for (int i = 0; i < spans.length; i++) {
            if (Integer.parseInt(spans[i]) == spanKhz) return i;
        }
        return 1;
    }

    // ===== AUDIO CAPTURE (CM108 via Android AudioRecord) =====

    private void initAudioCapture() {
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.DEFAULT,  // Use system default (CM108 should be selected in system settings)
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    BUFFER_SIZE
            );
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new IllegalStateException("AudioRecord initialization failed");
            }
            android.util.Log.i(TAG, "AudioRecord initialized: " + BUFFER_SIZE + " bytes");
        } catch (Exception e) {
            android.util.Log.e(TAG, "Failed to init AudioRecord", e);
            uiHandler.post(() -> tvStatus.setText("Audio init failed: " + e.getMessage()));
        }
    }

    private void startCapture() {
        if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            tvStatus.setText("Audio device not ready");
            return;
        }

        try {
            audioRecord.startRecording();
            capturing.set(true);
            tvStatus.setText("Capturing audio...");

            audioExecutor.execute(() -> {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (capturing.get() && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
                    if (read > 0) {
                        // Apply gain factor (simple scaling)
                        if (gainFactor != 50) {
                            float scale = gainFactor / 50f;
                            for (int i = 0; i < read; i += 2) {
                                short s = (short) ((buffer[i+1] << 8) | (buffer[i] & 0xFF));
                                s = (short) Math.max(-32768, Math.min(32767, s * scale));
                                buffer[i] = (byte) (s & 0xFF);
                                buffer[i+1] = (byte) ((s >> 8) & 0xFF);
                            }
                        }
                        // Feed to SpectrumView on UI thread
                        final byte[] data = buffer.clone();
                        final int len = read;
                        uiHandler.post(() -> spectrumView.update(data, 0, len));
                    }
                }
            });
        } catch (IllegalStateException e) {
            android.util.Log.e(TAG, "Failed to start recording", e);
            tvStatus.setText("Record start failed");
        }
    }

    private void stopCapture() {
        capturing.set(false);
        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            audioRecord.stop();
        }
        tvStatus.setText("Stopped");
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopCapture();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (audioRecord != null && audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            startCapture();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCapture();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        audioExecutor.shutdownNow();
    }
}