// MainActivity.java
package com.ar5000.app;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ar5000.core.protocol.Ar5000Command;
import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.model.ReceiverState;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.transport.UsbSerialTransport;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements Ar5000Controller.ResponseListener {

    private static final String TAG = "AR5000-MAIN";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    // UI
    private TextView tvStatus, tvLog;
    private Button btnConnect, btnReadStatus, btnExport, btnReadMemory, btnSend;
    private Spinner spinnerBank;
    private EditText etCommand;

    // Core
    // [FIXED] Делаем поля static для доступа из SettingsActivity
    public static Transport transport;
    public static Ar5000Controller controllerInstance;

    private ReceiverState state;
    private ExecutorService ioExecutor;

    // State
    private boolean isConnected = false;
    private StringBuilder logBuffer = new StringBuilder();
    private static final int MAX_LOG_LINES = 500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initCore();
        setupListeners();
        requestPermissions();
    }

    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvLog = findViewById(R.id.tvLog);
        btnConnect = findViewById(R.id.btnConnect);
        btnReadStatus = findViewById(R.id.btnReadStatus);
        btnExport = findViewById(R.id.btnExport);
        btnReadMemory = findViewById(R.id.btnReadMemory);
        btnSend = findViewById(R.id.btnSend);
        spinnerBank = findViewById(R.id.spinnerBank);
        etCommand = findViewById(R.id.etCommand);

        // Bank spinner: 0-9
        String[] banks = new String[10];
        for (int i = 0; i < 10; i++) banks[i] = "Bank " + i;
        spinnerBank.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, banks));
    }

    private void initCore() {
        ioExecutor = Executors.newSingleThreadExecutor();
        transport = new UsbSerialTransport((UsbManager) getSystemService(Context.USB_SERVICE));
        controllerInstance = new Ar5000Controller(transport); // [FIXED] присваиваем static поле
        controllerInstance.setResponseListener(this);
        state = controllerInstance.getCurrentState();
    }

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> toggleConnection());
        btnReadStatus.setOnClickListener(v -> readStatus());
        btnExport.setOnClickListener(v -> exportLog());
        btnReadMemory.setOnClickListener(v -> readMemoryBank());
        btnSend.setOnClickListener(v -> sendCommand());

        etCommand.setOnEditorActionListener((v, actionId, event) -> {
            sendCommand();
            return true;
        });
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void toggleConnection() {
        if (isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        log("Connecting...");
        ioExecutor.execute(() -> {
            if (transport.connect()) {
                isConnected = true;
                runOnUiThread(() -> {
                    tvStatus.setText("Connected");
                    btnConnect.setText("Disconnect");
                    controllerInstance.startListening();
                    log("Transport connected");
                });
            } else {
                runOnUiThread(() -> {
                    tvStatus.setText("Connect failed");
                    log("Transport connect failed");
                });
            }
        });
    }

    private void disconnect() {
        if (transport != null) {
            transport.disconnect();
            isConnected = false;
            tvStatus.setText("Disconnected");
            btnConnect.setText("Connect");
            log("Disconnected");
        }
    }

    private void readStatus() {
        if (!isConnected) { log("Not connected"); return; }
        log("Reading status (RX)...");
        sendCommandDirect(CommandFactory.getStatus());
    }

    private void readMemoryBank() {
        if (!isConnected) { log("Not connected"); return; }
        int bank = spinnerBank.getSelectedItemPosition();
        log("Reading memory bank " + bank + " (100 channels)...");

        ioExecutor.execute(() -> {
            state.resetMemoryBank(bank); // clear local cache
            for (int ch = 0; ch < 100; ch++) {
                if (!isConnected) break;
                Ar5000Command cmd = CommandFactory.readMemory(bank, ch);
                sendCommandDirect(cmd);
                try { Thread.sleep(30); } catch (InterruptedException ignored) {} // avoid flooding
            }
            state.markMemoryLoaded(bank);
            runOnUiThread(() -> log("Bank " + bank + " read complete. Loaded: " + state.getLoadedChannelCount(bank) + "/100"));
        });
    }

    private void sendCommand() {
        String cmdStr = etCommand.getText().toString().trim();
        if (cmdStr.isEmpty()) return;

        log("TX: " + cmdStr);

        // Try to build via CommandFactory for known commands, else send raw
        Ar5000Command cmd = buildCommandFromString(cmdStr);
        if (cmd != null) {
            sendCommandDirect(cmd);
        } else {
            // Fallback: send raw string + CRLF
            sendRawCommand(cmdStr);
        }
        etCommand.setText("");
    }

    private Ar5000Command buildCommandFromString(String cmdStr) {
        // Minimal parser for demo - expand as needed
        String[] parts = cmdStr.toUpperCase().split("\\s+");
        if (parts.length == 0) return null;

        String header = parts[0];
        switch (header) {
            case "MD": if (parts.length >= 2) return CommandFactory.setMode(Integer.parseInt(parts[1])); break;
            case "BW": if (parts.length >= 2) return CommandFactory.setBandwidth(Integer.parseInt(parts[1])); break;
            case "AT": if (parts.length >= 2) return CommandFactory.setAttenuator(Integer.parseInt(parts[1])); break;
            case "ST": if (parts.length >= 2) return CommandFactory.setStep(Long.parseLong(parts[1])); break;
            case "RX": return CommandFactory.getStatus();
            case "VR": return CommandFactory.getVersion();
            // Add more as needed
        }
        return null;
    }

    private void sendCommandDirect(Ar5000Command cmd) {
        if (!isConnected || cmd == null) return;
        ioExecutor.execute(() -> {
            try {
                transport.write(cmd.buildPacket());
            } catch (IOException e) {
                log("TX error: " + e.getMessage());
            }
        });
    }

    private void sendRawCommand(String cmdStr) {
        if (!isConnected) return;
        ioExecutor.execute(() -> {
            try {
                String packet = cmdStr.trim() + "\r\n";
                transport.write(packet.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                log("TX raw error: " + e.getMessage());
            }
        });
    }

    private void exportLog() {
        if (!hasStoragePermission()) {
            requestPermissions();
            return;
        }

        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File ar5000Dir = new File(downloads, "AR5000");
        if (!ar5000Dir.exists()) ar5000Dir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        File statusFile = new File(ar5000Dir, "status_" + timestamp + ".txt");
        File memoryFile = new File(ar5000Dir, "memory_bank_" + spinnerBank.getSelectedItemPosition() + "_" + timestamp + ".csv");

        ioExecutor.execute(() -> {
            try {
                // Export current state
                try (FileWriter fw = new FileWriter(statusFile)) {
                    fw.write("AR5000 Status Export\n");
                    fw.write("Time: " + timestamp + "\n\n");
                    fw.write("VFO: " + state.getVfo() + "\n");
                    fw.write("Frequency: " + state.getFrequencyHz() + " Hz\n");
                    fw.write("Mode: " + state.getModeString() + "\n");
                    fw.write("Bandwidth: " + state.getBwString() + "\n");
                    fw.write("Step: " + state.getStepHz() + " Hz\n");
                    fw.write("Attenuator: " + state.getAttenuatorString() + "\n");
                    fw.write("Antenna: " + state.getAntennaString() + "\n");
                    fw.write("Squelch: " + state.getSquelchLevel() + (state.isSquelchOpen() ? " (open)" : "") + "\n");
                    fw.write("AGC Level: " + state.getAgcLevel() + "\n");
                    fw.write("Signal: " + state.getSignalStrength() + "\n");
                    fw.write("Firmware: " + state.getFirmwareVersion() + "\n");
                }

                // Export memory bank if loaded
                int bank = spinnerBank.getSelectedItemPosition();
                if (state.isMemoryLoaded(bank)) {
                    try (FileWriter fw = new FileWriter(memoryFile)) {
                        fw.write("Channel,Frequency_Hz,Mode,Bandwidth,Memo\n");
                        for (int ch = 0; ch < 100; ch++) {
                            ReceiverState.MemoryChannel mc = state.getMemoryChannel(bank, ch);
                            if (mc != null && !mc.isEmpty) {
                                fw.write(String.format("%d,%d,%s,%s,\"%s\"\n",
                                        ch, mc.frequencyHz, mc.getModeString(), mc.getBwString(), mc.getMemoText()));
                            }
                        }
                    }
                }

                runOnUiThread(() -> {
                    log("Exported: " + statusFile.getName());
                    if (state.isMemoryLoaded(bank)) log("Exported: " + memoryFile.getName());
                    Toast.makeText(this, "Exported to Downloads/AR5000/", Toast.LENGTH_LONG).show();
                });
            } catch (IOException e) {
                runOnUiThread(() -> log("Export error: " + e.getMessage()));
            }
        });
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ===== LOGGING =====

    private void log(String msg) {
        String timestamp = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
        String line = "[" + timestamp + "] " + msg + "\n";

        runOnUiThread(() -> {
            logBuffer.insert(0, line);
            // Trim old lines
            String[] lines = logBuffer.toString().split("\n");
            if (lines.length > MAX_LOG_LINES) {
                logBuffer = new StringBuilder();
                for (int i = 0; i < MAX_LOG_LINES; i++) {
                    logBuffer.append(lines[i]).append("\n");
                }
            }
            tvLog.setText(logBuffer.toString());
            // Auto-scroll to top (newest first)
            tvLog.post(() -> tvLog.scrollTo(0, 0));
        });

        Log.d(TAG, msg);
    }

    // ===== RESPONSE LISTENER =====

    @Override public void onFrequencyChanged(String vfo, long freqHz) {
        log("RX: FREQ " + vfo + "=" + freqHz);
    }

    @Override public void onModeChanged(int modeCode) {
        log("RX: MODE=" + modeCode + " (" + state.getModeString() + ")");
    }

    @Override public void onBandwidthChanged(int bwCode) {
        log("RX: BW=" + bwCode + " (" + state.getBwString() + ")");
    }

    @Override public void onSignalStrength(int sValue) {
        log("RX: SIGNAL=S" + (sValue / 10));
    }

    @Override public void onBusy(boolean busy) {
        log("RX: BUSY=" + busy);
    }

    @Override public void onError(String message) {
        log("RX: ERROR: " + message);
    }

    @Override public void onRawStatus(String raw) {
        log("RX: RAW [" + raw + "]");
        // Auto-parse known responses
        if (raw.startsWith("VR")) {
            state.setFirmwareVersion(raw.substring(2).trim());
        } else if (raw.startsWith("MA")) {
            // Parse memory response: MAnmm RF... MD... BW...
            parseMemoryResponse(raw);
        }
    }

    @Override public void onStateChanged(ReceiverState newState) {
        // State already updated in controller, just log key changes
        // (avoid spam - log only on explicit user action)
    }

    private void parseMemoryResponse(String raw) {
        // Format: MAnmm RF145000000 MD0 BW3 TMTEXT
        try {
            String[] tokens = raw.trim().split("\\s+");
            if (tokens.length < 4) return;

            String maToken = tokens[0]; // MAnmm
            int bank = Integer.parseInt(maToken.substring(1, 2));
            int channel = Integer.parseInt(maToken.substring(2));

            long freq = 0;
            int mode = 0, bw = 0;
            String memo = "";

            for (String t : tokens) {
                if (t.startsWith("RF")) freq = Long.parseLong(t.substring(2));
                else if (t.startsWith("MD")) mode = Integer.parseInt(t.substring(2));
                else if (t.startsWith("BW")) bw = Integer.parseInt(t.substring(2));
                else if (t.startsWith("TM")) memo = t.substring(2);
            }

            ReceiverState.MemoryChannel mc = ReceiverState.MemoryChannel.fromData(channel, freq, mode, bw, memo);
            state.setMemoryChannel(bank, channel, mc);
            log("MEM: Bank" + bank + " Ch" + channel + " " + mc.getFrequencyMhzString());
        } catch (Exception e) {
            log("MEM parse error: " + e.getMessage());
        }
    }

    // ===== LIFECYCLE =====

    @Override protected void onDestroy() {
        super.onDestroy();
        disconnect();
        ioExecutor.shutdownNow();
    }

    @Override protected void onPause() {
        super.onPause();
        // Optional: auto-export on pause
        // exportLog();
    }

    // ===== [ADDED] PUBLIC STATIC GETTERS FOR SettingsActivity =====

    /**
     * Публичный доступ к transport для SettingsActivity.
     * Использовать осторожно: только для отправки команд из настроек.
     * @return текущий Transport или null если не инициализирован
     */
    public static Transport getTransport() {
        return transport;
    }

    /**
     * Публичный доступ к controller для SettingsActivity.
     * @return текущий Ar5000Controller или null если не инициализирован
     */
    public static Ar5000Controller getController() {
        return controllerInstance;
    }
}