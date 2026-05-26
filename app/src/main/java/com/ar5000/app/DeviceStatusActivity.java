// DeviceStatusActivity.java
package com.ar5000.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.model.ReceiverState;
import com.ar5000.core.transport.Transport;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeviceStatusActivity extends AppCompatActivity {

    private static final String TAG = "AR5000-STATUS";

    // UI references - Core state
    private TextView tvVfo, tvFrequency, tvMode, tvBandwidth, tvStep, tvAttenuator, tvAntenna;
    private TextView tvSquelch, tvAgcLevel, tvSignal, tvMemo;

    // UI references - Extended state
    private TextView tvHpf, tvLpf, tvCwPitch, tvDeEmphasis, tvTuneSelect, tvLevelSq;
    private TextView tvVoiceLevel, tvDelayTime, tvCyberScan, tvManualTune, tvFirmware;

    // UI references - Status flags
    private TextView tvRemoteMode, tvBusy, tvLastUpdate;

    // UI references - Memory section
    private LinearLayout layoutMemoryBanks;

    // [ADDED] UI references - All VFOs table (A-E)
    private TextView[] tvVfoFreq = new TextView[5];
    private TextView[] tvVfoMode = new TextView[5];
    private TextView[] tvVfoBw = new TextView[5];

    // Controls
    private Button btnRefresh;

    // Core
    private Transport transport;
    private Ar5000Controller controller;
    private ReceiverState state;
    private ExecutorService ioExecutor;
    private Handler uiHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_status);

        initViews();
        initCore();
        setupListeners();
        loadStatus();
    }

    private void initViews() {
        // Core state
        tvVfo = findViewById(R.id.tvVfo);
        tvFrequency = findViewById(R.id.tvFrequency);
        tvMode = findViewById(R.id.tvMode);
        tvBandwidth = findViewById(R.id.tvBandwidth);
        tvStep = findViewById(R.id.tvStep);
        tvAttenuator = findViewById(R.id.tvAttenuator);
        tvAntenna = findViewById(R.id.tvAntenna);
        tvSquelch = findViewById(R.id.tvSquelch);
        tvAgcLevel = findViewById(R.id.tvAgcLevel);
        tvSignal = findViewById(R.id.tvSignal);
        tvMemo = findViewById(R.id.tvMemo);

        // Extended state
        tvHpf = findViewById(R.id.tvHpf);
        tvLpf = findViewById(R.id.tvLpf);
        tvCwPitch = findViewById(R.id.tvCwPitch);
        tvDeEmphasis = findViewById(R.id.tvDeEmphasis);
        tvTuneSelect = findViewById(R.id.tvTuneSelect);
        tvLevelSq = findViewById(R.id.tvLevelSq);
        tvVoiceLevel = findViewById(R.id.tvVoiceLevel);
        tvDelayTime = findViewById(R.id.tvDelayTime);
        tvCyberScan = findViewById(R.id.tvCyberScan);
        tvManualTune = findViewById(R.id.tvManualTune);
        tvFirmware = findViewById(R.id.tvFirmware);

        // Status flags
        tvRemoteMode = findViewById(R.id.tvRemoteMode);
        tvBusy = findViewById(R.id.tvBusy);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);

        // Memory section
        layoutMemoryBanks = findViewById(R.id.layoutMemoryBanks);

        // [ADDED] All VFOs table initialization
        tvVfoFreq[0] = findViewById(R.id.tvVfoA_freq); tvVfoMode[0] = findViewById(R.id.tvVfoA_mode); tvVfoBw[0] = findViewById(R.id.tvVfoA_bw);
        tvVfoFreq[1] = findViewById(R.id.tvVfoB_freq); tvVfoMode[1] = findViewById(R.id.tvVfoB_mode); tvVfoBw[1] = findViewById(R.id.tvVfoB_bw);
        tvVfoFreq[2] = findViewById(R.id.tvVfoC_freq); tvVfoMode[2] = findViewById(R.id.tvVfoC_mode); tvVfoBw[2] = findViewById(R.id.tvVfoC_bw);
        tvVfoFreq[3] = findViewById(R.id.tvVfoD_freq); tvVfoMode[3] = findViewById(R.id.tvVfoD_mode); tvVfoBw[3] = findViewById(R.id.tvVfoD_bw);
        tvVfoFreq[4] = findViewById(R.id.tvVfoE_freq); tvVfoMode[4] = findViewById(R.id.tvVfoE_mode); tvVfoBw[4] = findViewById(R.id.tvVfoE_bw);

        // Controls
        btnRefresh = findViewById(R.id.btnRefresh);
    }

    private void initCore() {
        uiHandler = new Handler(Looper.getMainLooper());
        ioExecutor = Executors.newSingleThreadExecutor();
        transport = MainActivity.getTransport();
        controller = MainActivity.getController();
        if (controller != null) {
            state = controller.getCurrentState();
        }
    }

    private void setupListeners() {
        btnRefresh.setOnClickListener(v -> loadStatus());
    }

    private void loadStatus() {
        if (transport == null || !transport.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }

        btnRefresh.setEnabled(false);
        btnRefresh.setText("Reading...");

        ioExecutor.execute(() -> {
            // Request full status dump for active VFO
            if (controller != null) {
                controller.send(CommandFactory.getStatus());
                // Request version
                controller.send(CommandFactory.getVersion());
                // Request AGC level
                controller.send(CommandFactory.getAgcLevel(true));

                // [ADDED] Request frequencies for all non-active VFOs (A-E)
                String activeVfo = state != null ? state.getVfo() : "A";
                for (char vfo = 'A'; vfo <= 'E'; vfo++) {
                    String vfoStr = String.valueOf(vfo);
                    if (vfoStr.equals(activeVfo)) continue; // already in RX dump
                    controller.send(CommandFactory.getFrequency(vfoStr));
                }
            }

            // Wait a bit for responses, then update UI
            try { Thread.sleep(800); } catch (InterruptedException ignored) {}

            updateUiFromState();

            // Load memory bank info (just counts, not full data)
            updateMemoryBankInfo();

            uiHandler.post(() -> {
                btnRefresh.setEnabled(true);
                btnRefresh.setText("Refresh All");
                updateTimestamp();
            });
        });
    }

    private void updateUiFromState() {
        if (state == null) return;

        uiHandler.post(() -> {
            // Core state (active VFO)
            tvVfo.setText(state.getVfo());
            tvFrequency.setText(formatFrequency(state.getFrequencyHz()));
            tvMode.setText(state.getModeString());
            tvBandwidth.setText(state.getBwString());
            tvStep.setText(state.getStepHz() + " Hz");
            tvAttenuator.setText(state.getAttenuatorString());
            tvAntenna.setText(state.getAntennaString());
            tvSquelch.setText(state.getSquelchLevel() + (state.isSquelchOpen() ? " (open)" : ""));
            tvAgcLevel.setText(String.valueOf(state.getAgcLevel()));
            tvSignal.setText("S" + (state.getSignalStrength() / 10));
            tvMemo.setText(state.getMemoText());

            // Extended state
            tvHpf.setText(state.getHpfString());
            tvLpf.setText(state.getLpfString());
            tvCwPitch.setText(state.getCwPitchString());
            tvDeEmphasis.setText(state.getDeEmphasisMode() == 0 ? "OFF" : "ON");
            tvTuneSelect.setText(String.valueOf(state.getTuneSelectValue()));
            tvLevelSq.setText(String.valueOf(state.getLevelSqValue()));
            tvVoiceLevel.setText(String.valueOf(state.getVoiceLevelValue()));
            tvDelayTime.setText(String.format("%.1f s", state.getDelayTimeSec()));
            tvCyberScan.setText(state.isCyberScanEnabled() ? "ON" : "OFF");
            tvManualTune.setText(state.isManualTuneEnabled() ? "ON" : "OFF");
            tvFirmware.setText(state.getFirmwareVersion());

            // Status flags
            tvRemoteMode.setText(state.isRemoteMode() ? "YES" : "NO");
            tvBusy.setText(state.isBusy() ? "YES" : "NO");

            // [ADDED] Update All VFOs table (A-E)
            updateAllVfosTable();
        });
    }

    /**
     * [ADDED] Updates the table showing all 5 VFOs (A-E).
     * Uses ReceiverState.getAllVfoStates() for thread-safe access.
     */
    private void updateAllVfosTable() {
        if (state == null) return;

        ReceiverState.VfoState[] vfos = state.getAllVfoStates();
        for (int i = 0; i < 5; i++) {
            ReceiverState.VfoState vs = vfos[i];
            if (vs != null) {
                tvVfoFreq[i].setText(vs.getFrequencyMhzString());
                tvVfoMode[i].setText(vs.getModeString());
                tvVfoBw[i].setText(vs.getBwString());
            }
        }
    }

    private void updateMemoryBankInfo() {
        if (state == null) return;

        uiHandler.post(() -> {
            layoutMemoryBanks.removeAllViews();

            for (int bank = 0; bank < 10; bank++) {
                boolean loaded = state.isMemoryLoaded(bank);
                int count = state.getLoadedChannelCount(bank);

                TextView row = new TextView(this);
                row.setPadding(8, 4, 8, 4);
                row.setText(String.format("Bank %d: %s (%d/100 channels)",
                        bank, loaded ? "loaded" : "not loaded", count));
                row.setTextColor(loaded ? 0xFF00aa00 : 0xFF888888);

                layoutMemoryBanks.addView(row);
            }
        });
    }

    private void updateTimestamp() {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        tvLastUpdate.setText("Last update: " + ts);
    }

    private String formatFrequency(long hz) {
        long mhz = hz / 1_000_000;
        long khz = (hz / 1000) % 1000;
        long hzPart = hz % 1000;
        return String.format("%d.%03d.%03d MHz", mhz, khz, hzPart);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }
}