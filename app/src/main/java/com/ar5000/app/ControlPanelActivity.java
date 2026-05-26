// ControlPanelActivity.java
package com.ar5000.app;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.protocol.Ar5000Protocol;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.model.ReceiverState;
import com.ar5000.script.RadioApi;
import com.ar5000.script.ScriptEngine;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ControlPanelActivity extends AppCompatActivity {

    private static final String TAG = "AR5000-CONTROL";
    private static final String PREFS_NAME = "ar5000_control_prefs";
    private static final String CONN_PREFS = "ar5000_conn_prefs";
    private static final int PERMISSION_REQUEST_CODE = 1002;
    private static final int LUA_FILE_PICKER = 1001;

    // ===== UI REFERENCES =====
    private TextView tvConnStatus, tvLog, tvLuaLog;
    private Spinner spVfo, spMode, spBw, spStep, spAtt, spAnt, spHpf, spLpf;
    private Spinner spCtcss, spCwPitch, spSubStep, spScanBank, spAutoStore;
    private Spinner spMemBank, spMemCh;
    private EditText etFreq, etSearchLow, etSearchHigh, etMemoText;
    private SeekBar sbSquelch, sbBeep, sbToneElim, sbScramble, sbScanLevel, sbScanDelay;
    private TextView tvSquelchVal, tvBeepVal, tvToneElimVal, tvScrambleVal, tvScanLevelVal, tvScanDelayVal;
    private Switch swAutoMode, swAgcSend, swDtmf, swManualTune, swCyberScan, swAfc, swDeEmph;
    private Button btnSetFreq, btnSetSearch, btnSetMemo, btnWriteMem, btnClearMem;
    private Button btnApplyAll, btnDefaults, btnExport, btnImport;

    // ===== CONNECTION SETTINGS UI =====
    private Spinner spTransportType, spBaud;
    private EditText etIp, etPort;
    private Button btnApplyConn;
    private ArrayAdapter<String> transportAdapter, baudAdapter;

    // ===== LUA INTEGRATION UI =====
    private Spinner spLuaScripts;
    private Button btnLoadLua, btnRunLua, btnStopLua;
    private ArrayAdapter<String> luaAdapter;
    private List<String> luaFileList = new ArrayList<>();

    // ===== CORE =====
    private Transport transport;
    private Ar5000Controller controller;
    private ReceiverState state;
    private ExecutorService ioExecutor;
    private Handler uiHandler;
    private SharedPreferences prefs;

    // ===== LUA ENGINE =====
    private ScriptEngine luaEngine;
    private RadioApi radioApi;

    // ===== ADAPTERS & DATA =====
    private ArrayAdapter<String> vfoAdapter, modeAdapter, bwAdapter, stepAdapter;
    private ArrayAdapter<String> attAdapter, antAdapter, hpfAdapter, lpfAdapter;
    private ArrayAdapter<String> ctcssAdapter, cwPitchAdapter, subStepAdapter;
    private ArrayAdapter<String> scanBankAdapter, autoStoreAdapter, memBankAdapter, memChAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_panel);

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        initCore();
        initViews();
        initAdapters();
        setupListeners();
        loadConnectionSettings();
        loadSavedSettings();
        syncUiWithState();
        initLuaEngine();
        scanLuaScripts();
    }

    private void initCore() {
        uiHandler = new Handler(Looper.getMainLooper());
        ioExecutor = Executors.newSingleThreadExecutor();
        transport = MainActivity.getTransport();
        controller = MainActivity.getController();
        if (controller != null) {
            state = controller.getCurrentState();
        }
        updateConnStatus();
    }

    private void initViews() {
        tvConnStatus = findViewById(R.id.tvConnStatus);
        tvLog = findViewById(R.id.tvLog);

        // VFO & Frequency
        spVfo = findViewById(R.id.spVfo);
        etFreq = findViewById(R.id.etFreq);
        btnSetFreq = findViewById(R.id.btnSetFreq);

        // Mode & Bandwidth
        spMode = findViewById(R.id.spMode);
        spBw = findViewById(R.id.spBw);
        spStep = findViewById(R.id.spStep);

        // Filters & Attenuator
        spAtt = findViewById(R.id.spAtt);
        spAnt = findViewById(R.id.spAnt);
        spHpf = findViewById(R.id.spHpf);
        spLpf = findViewById(R.id.spLpf);

        // Squelch & Audio
        sbSquelch = findViewById(R.id.sbSquelch);
        sbBeep = findViewById(R.id.sbBeep);
        tvSquelchVal = findViewById(R.id.tvSquelchVal);
        tvBeepVal = findViewById(R.id.tvBeepVal);

        // Toggles
        swAutoMode = findViewById(R.id.swAutoMode);
        swAgcSend = findViewById(R.id.swAgcSend);
        swDtmf = findViewById(R.id.swDtmf);
        swManualTune = findViewById(R.id.swManualTune);
        swCyberScan = findViewById(R.id.swCyberScan);
        swAfc = findViewById(R.id.swAfc);
        swDeEmph = findViewById(R.id.swDeEmph);

        // Tones & Special
        spCtcss = findViewById(R.id.spCtcss);
        sbToneElim = findViewById(R.id.sbToneElim);
        sbScramble = findViewById(R.id.sbScramble);
        spCwPitch = findViewById(R.id.spCwPitch);
        spSubStep = findViewById(R.id.spSubStep);
        tvToneElimVal = findViewById(R.id.tvToneElimVal);
        tvScrambleVal = findViewById(R.id.tvScrambleVal);

        // Scan Settings
        spScanBank = findViewById(R.id.spScanBank);
        spAutoStore = findViewById(R.id.spAutoStore);
        etSearchLow = findViewById(R.id.etSearchLow);
        etSearchHigh = findViewById(R.id.etSearchHigh);
        btnSetSearch = findViewById(R.id.btnSetSearch);
        sbScanLevel = findViewById(R.id.sbScanLevel);
        sbScanDelay = findViewById(R.id.sbScanDelay);
        tvScanLevelVal = findViewById(R.id.tvScanLevelVal);
        tvScanDelayVal = findViewById(R.id.tvScanDelayVal);

        // Memory Operations
        spMemBank = findViewById(R.id.spMemBank);
        spMemCh = findViewById(R.id.spMemCh);
        etMemoText = findViewById(R.id.etMemoText);
        btnSetMemo = findViewById(R.id.btnSetMemo);
        btnWriteMem = findViewById(R.id.btnWriteMem);
        btnClearMem = findViewById(R.id.btnClearMem);

        // Config Management
        btnDefaults = findViewById(R.id.btnDefaults);
        btnExport = findViewById(R.id.btnExport);
        btnImport = findViewById(R.id.btnImport);

        // Connection Settings [ADDED]
        spTransportType = findViewById(R.id.spTransportType);
        etIp = findViewById(R.id.etIp);
        etPort = findViewById(R.id.etPort);
        spBaud = findViewById(R.id.spBaud);
        btnApplyConn = findViewById(R.id.btnApplyConn);

        // Lua Integration [ADDED]
        spLuaScripts = findViewById(R.id.spLuaScripts);
        btnLoadLua = findViewById(R.id.btnLoadLua);
        btnRunLua = findViewById(R.id.btnRunLua);
        btnStopLua = findViewById(R.id.btnStopLua);
        tvLuaLog = findViewById(R.id.tvLuaLog);

        // Apply All
        btnApplyAll = findViewById(R.id.btnApplyAll);
    }

    private void initAdapters() {
        // VFO: A-E
        vfoAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"A", "B", "C", "D", "E"});
        vfoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spVfo.setAdapter(vfoAdapter);

        // Mode: FM, AM, LSB, USB, CW
        modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Ar5000Protocol.MODE_NAMES);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMode.setAdapter(modeAdapter);

        // Bandwidth: AUTO, 0.5K, 3K, 6K, 15K, 40K, 110K, 220K
        bwAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Ar5000Protocol.BW_NAMES);
        bwAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBw.setAdapter(bwAdapter);

        // Step: presets in Hz
        String[] steps = {"50", "100", "500", "1000", "5000", "10000", "25000", "50000", "100000", "500000", "1000000"};
        stepAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, steps);
        stepAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spStep.setAdapter(stepAdapter);

        // Attenuator: 0dB, 10dB, 20dB, AUTO
        attAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"0dB", "10dB", "20dB", "AUTO"});
        attAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAtt.setAdapter(attAdapter);

        // Antenna: AUTO, 1, 2, 3, 4
        antAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"AUTO", "1", "2", "3", "4"});
        antAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAnt.setAdapter(antAdapter);

        // HPF: 0.05K, 0.2K, 0.3K, 0.4K
        hpfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"0.05K", "0.2K", "0.3K", "0.4K"});
        hpfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spHpf.setAdapter(hpfAdapter);

        // LPF: 3K, 4K, 6K, 12K
        lpfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"3K", "4K", "6K", "12K"});
        lpfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLpf.setAdapter(lpfAdapter);

        // CTCSS: OFF, SEARCH, 67.0Hz...254.1Hz (simplified: 0-37)
        String[] ctcss = new String[38];
        ctcss[0] = "OFF"; ctcss[1] = "SEARCH";
        for (int i = 2; i < 38; i++) ctcss[i] = "Code " + i;
        ctcssAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ctcss);
        ctcssAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCtcss.setAdapter(ctcssAdapter);

        // CW Pitch: 400Hz-1100Hz step 100
        String[] cwPitches = {"400Hz", "500Hz", "600Hz", "700Hz", "800Hz", "900Hz", "1000Hz", "1100Hz"};
        cwPitchAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, cwPitches);
        cwPitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCwPitch.setAdapter(cwPitchAdapter);

        // Sub-Step: 0-A (hex)
        String[] subSteps = new String[11];
        for (int i = 0; i <= 10; i++) subSteps[i] = String.format("%X", i);
        subStepAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, subSteps);
        subStepAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSubStep.setAdapter(subStepAdapter);

        // Scan Bank: 0-19
        String[] scanBanks = new String[20];
        for (int i = 0; i < 20; i++) scanBanks[i] = String.valueOf(i);
        scanBankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, scanBanks);
        scanBankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spScanBank.setAdapter(scanBankAdapter);

        // Auto-Store: OFF, ON, AUTO
        autoStoreAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"OFF", "ON", "AUTO"});
        autoStoreAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAutoStore.setAdapter(autoStoreAdapter);

        // Memory Bank: 0-9
        String[] banks = new String[10];
        for (int i = 0; i < 10; i++) banks[i] = String.valueOf(i);
        memBankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, banks);
        memBankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMemBank.setAdapter(memBankAdapter);

        // Memory Channel: 00-99
        String[] channels = new String[100];
        for (int i = 0; i < 100; i++) channels[i] = String.format("%02d", i);
        memChAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, channels);
        memChAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMemCh.setAdapter(memChAdapter);

        // Transport type
        transportAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"USB Serial", "WiFi / IP"});
        transportAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTransportType.setAdapter(transportAdapter);

        // Baud rate
        baudAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"4800", "9600", "19200", "38400", "57600", "115200"});
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBaud.setAdapter(baudAdapter);

        // Lua scripts adapter
        luaAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, luaFileList);
        luaAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLuaScripts.setAdapter(luaAdapter);
    }

    private void setupListeners() {
        // ===== CONFIG MANAGEMENT =====
        btnDefaults.setOnClickListener(v -> resetToDefaults());
        btnExport.setOnClickListener(v -> exportSettings());
        btnImport.setOnClickListener(v -> importSettings());

        // Connection status update
        btnApplyAll.setOnClickListener(v -> applyAllSettings());

        // Frequency set
        btnSetFreq.setOnClickListener(v -> {
            String freqStr = etFreq.getText().toString().trim();
            if (!freqStr.isEmpty()) {
                try {
                    double freqMhz = Double.parseDouble(freqStr);
                    long freqHz = (long) (freqMhz * 1_000_000);
                    String vfo = spVfo.getSelectedItem().toString();
                    sendCommand(CommandFactory.setFrequency(vfo, freqHz));
                    log("Set freq: " + vfo + "=" + freqHz);
                    saveSettings();
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid frequency", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Spinners: send command on selection change + auto-save
        setupSpinnerWithSave(spMode, (pos) -> {
            sendCommand(CommandFactory.setMode(pos));
            log("Mode: " + Ar5000Protocol.getModeName(pos));
        });

        setupSpinnerWithSave(spBw, (pos) -> {
            sendCommand(CommandFactory.setBandwidth(pos));
            log("BW: " + Ar5000Protocol.getBwName(pos));
        });

        setupSpinnerWithSave(spStep, (pos) -> {
            long step = Long.parseLong(stepAdapter.getItem(pos).replace("Hz", "").replace("K", "000"));
            sendCommand(CommandFactory.setStep(step));
            log("Step: " + step + " Hz");
        });

        setupSpinnerWithSave(spAtt, (pos) -> {
            sendCommand(CommandFactory.setAttenuator(pos));
            log("Att: " + attAdapter.getItem(pos));
        });

        setupSpinnerWithSave(spAnt, (pos) -> {
            String val = pos == 0 ? "AUTO" : String.valueOf(pos);
            sendCommand(CommandFactory.setAntenna(val));
            log("Ant: " + val);
        });

        setupSpinnerWithSave(spHpf, (pos) -> {
            sendCommand(CommandFactory.setHpf(pos));
            log("HPF: " + hpfAdapter.getItem(pos));
        });

        setupSpinnerWithSave(spLpf, (pos) -> {
            sendCommand(CommandFactory.setLpf(pos));
            log("LPF: " + lpfAdapter.getItem(pos));
        });

        // SeekBars: update label + send command on stop + auto-save
        setupSeekBarWithSave(sbSquelch, tvSquelchVal, val -> {
            sendCommand(CommandFactory.setSquelch(val));
            log("Squelch: " + val);
        });

        setupSeekBarWithSave(sbBeep, tvBeepVal, val -> {
            sendCommand(CommandFactory.setBeep(val));
            log("Beep: " + val);
        });

        setupSeekBarWithSave(sbToneElim, tvToneElimVal, val -> {
            sendCommand(CommandFactory.setToneElim(val));
            log("ToneElim: " + val);
        });

        setupSeekBarWithSave(sbScramble, tvScrambleVal, val -> {
            sendCommand(CommandFactory.setScramble(val));
            log("Scramble: " + val);
        });

        setupSeekBarWithSave(sbScanLevel, tvScanLevelVal, val -> {
            sendCommand(CommandFactory.setLevelScan(val));
            log("ScanLevel: " + val);
        });

        setupSeekBarWithSave(sbScanDelay, tvScanDelayVal, val -> {
            sendCommand(CommandFactory.setSearchDelay(val / 10f));
            log("ScanDelay: " + (val / 10f) + "s");
        });

        // Switches with auto-save
        setupSwitchWithSave(swAutoMode, (on) -> {
            sendCommand(CommandFactory.setAutoMode(on));
            log("AutoMode: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swAgcSend, (on) -> {
            sendCommand(CommandFactory.setAgcLevelSend(on));
            log("AgcSend: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swDtmf, (on) -> {
            sendCommand(CommandFactory.setDtmf(on));
            log("DTMF: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swManualTune, (on) -> {
            sendCommand(CommandFactory.setManualTune(on));
            log("ManualTune: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swCyberScan, (on) -> {
            sendCommand(CommandFactory.setCyberScan(on));
            log("CyberScan: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swAfc, (on) -> {
            sendCommand(CommandFactory.setAfc(on));
            log("AFC: " + (on ? "ON" : "OFF"));
        });

        setupSwitchWithSave(swDeEmph, (on) -> {
            sendCommand(CommandFactory.setDeEmphasis(on ? 1 : 0));
            log("DeEmph: " + (on ? "ON" : "OFF"));
        });

        // CTCSS
        setupSpinnerWithSave(spCtcss, (pos) -> {
            sendCommand(CommandFactory.setCtcss(pos));
            log("CTCSS: " + ctcssAdapter.getItem(pos));
        });

        // CW Pitch
        setupSpinnerWithSave(spCwPitch, (pos) -> {
            sendCommand(CommandFactory.setCwPitch(pos));
            log("CW Pitch: " + cwPitchAdapter.getItem(pos));
        });

        // Sub-Step
        setupSpinnerWithSave(spSubStep, (pos) -> {
            sendCommand(CommandFactory.setSubStep(pos));
            log("SubStep: " + subStepAdapter.getItem(pos));
        });

        // Search limits
        btnSetSearch.setOnClickListener(v -> {
            try {
                double low = Double.parseDouble(etSearchLow.getText().toString().trim());
                double high = Double.parseDouble(etSearchHigh.getText().toString().trim());
                sendCommand(CommandFactory.setSearchLower((long)(low * 1e6)));
                sendCommand(CommandFactory.setSearchUpper((long)(high * 1e6)));
                log("Search: " + low + "-" + high + " MHz");
                saveSettings();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid frequency", Toast.LENGTH_SHORT).show();
            }
        });

        // Memory memo (NO auto-save for memory operations)
        btnSetMemo.setOnClickListener(v -> {
            int ch = spMemCh.getSelectedItemPosition();
            String text = etMemoText.getText().toString().trim();
            sendCommand(CommandFactory.setTextMemo(ch, text));
            log("Memo Ch" + ch + ": \"" + text + "\"");
        });

        // Memory write (NO auto-save)
        btnWriteMem.setOnClickListener(v -> {
            int bank = spMemBank.getSelectedItemPosition();
            int ch = spMemCh.getSelectedItemPosition();
            long freq = state != null ? state.getFrequencyHz() : 145_000_000L;
            int mode = state != null ? state.getModeCode() : 0;
            int bw = state != null ? state.getBwCode() : 0;
            sendCommand(CommandFactory.writeMemory(bank, ch, freq, mode, bw));
            log("Write Mem: B" + bank + "C" + ch + " " + freq);
        });

        // Memory clear (NO auto-save)
        btnClearMem.setOnClickListener(v -> {
            int bank = spMemBank.getSelectedItemPosition();
            int ch = spMemCh.getSelectedItemPosition();
            sendCommand(CommandFactory.clearMemory(bank, ch));
            log("Clear Mem: B" + bank + "C" + ch);
        });

        // ===== CONNECTION SETTINGS =====
        btnApplyConn.setOnClickListener(v -> {
            saveConnectionSettings();
            Toast.makeText(this, "Connection settings saved. Reconnect in main screen.", Toast.LENGTH_LONG).show();
            log("Connection settings updated");
        });

        // ===== LUA SCRIPTS =====
        btnLoadLua.setOnClickListener(v -> {
            String selected = spLuaScripts.getSelectedItem() != null ? spLuaScripts.getSelectedItem().toString() : "";
            if (!selected.isEmpty()) {
                try {
                    String scriptPath = getLuaScriptPath(selected);
                    if (luaEngine != null && !scriptPath.isEmpty()) {
                        // ScriptEngine.run() требует InputStream, открываем файл
                        java.io.FileInputStream fis = new java.io.FileInputStream(scriptPath);
                        boolean success = luaEngine.run(fis);
                        fis.close();
                        if (success) {
                            appendLuaLog("Executed: " + selected);
                            log("Lua executed: " + selected);
                        } else {
                            appendLuaLog("Execution failed: " + selected);
                        }
                    }
                } catch (Exception e) {
                    appendLuaLog("Error: " + e.getMessage());
                    log("Lua error: " + e.getMessage());
                }
            }
        });

        btnRunLua.setOnClickListener(v -> {
            // В текущем API ScriptEngine нет отдельного "run" без загрузки
            // Предлагаем пользователю использовать кнопку Load
            appendLuaLog("Use 'Load' to execute a script");
            Toast.makeText(this, "Press 'Load' to run a script", Toast.LENGTH_SHORT).show();
        });

        btnStopLua.setOnClickListener(v -> {
            if (luaEngine != null) {
                luaEngine.stop();
                appendLuaLog("Stopped");
                log("Lua stopped");
            }
        });
    }

    // ===== HELPERS FOR AUTO-SAVE =====

    private void setupSpinnerWithSave(Spinner spinner, java.util.function.Consumer<Integer> onChange) {
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (onChange != null) onChange.accept(pos);
                saveSettings();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void setupSeekBarWithSave(SeekBar sb, TextView tv, java.util.function.Consumer<Integer> onChange) {
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int val, boolean from) {
                tv.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (onChange != null) onChange.accept(seekBar.getProgress());
                saveSettings();
            }
        });
    }

    private void setupSwitchWithSave(Switch sw, java.util.function.Consumer<Boolean> onChange) {
        sw.setOnCheckedChangeListener((btn, on) -> {
            if (onChange != null) onChange.accept(on);
            saveSettings();
        });
    }

    // ===== DEFAULT SETTINGS (EXCLUDES MEMORY) =====

    private void resetToDefaults() {
        uiHandler.post(() -> {
            spVfo.setSelection(0);
            etFreq.setText("145.000");
            spMode.setSelection(0);
            spBw.setSelection(3);
            spStep.setSelection(4);
            spAtt.setSelection(0);
            spAnt.setSelection(0);
            spHpf.setSelection(0);
            spLpf.setSelection(0);
            sbSquelch.setProgress(128); tvSquelchVal.setText("128");
            sbBeep.setProgress(100); tvBeepVal.setText("100");
            swAutoMode.setChecked(false);
            swAgcSend.setChecked(false);
            swDtmf.setChecked(false);
            swManualTune.setChecked(false);
            swCyberScan.setChecked(false);
            swAfc.setChecked(false);
            swDeEmph.setChecked(false);
            spCtcss.setSelection(0);
            sbToneElim.setProgress(0); tvToneElimVal.setText("0");
            sbScramble.setProgress(0); tvScrambleVal.setText("0");
            spCwPitch.setSelection(1);
            spSubStep.setSelection(0);
            spScanBank.setSelection(0);
            spAutoStore.setSelection(0);
            etSearchLow.setText("145.000");
            etSearchHigh.setText("146.000");
            sbScanLevel.setProgress(100); tvScanLevelVal.setText("100");
            sbScanDelay.setProgress(5); tvScanDelayVal.setText("0.5s");
            etMemoText.setText("");
            log("Defaults applied (memory preserved)");
            Toast.makeText(this, "Defaults applied (memory unchanged)", Toast.LENGTH_SHORT).show();
            saveSettings();
        });
    }

    // ===== PERSISTENCE: SAVE/LOAD SETTINGS =====

    private void saveSettings() {
        SharedPreferences.Editor e = prefs.edit();
        e.putString("vfo", spVfo.getSelectedItem().toString());
        e.putString("freq", etFreq.getText().toString().trim());
        e.putInt("mode", spMode.getSelectedItemPosition());
        e.putInt("bw", spBw.getSelectedItemPosition());
        e.putInt("step", spStep.getSelectedItemPosition());
        e.putInt("att", spAtt.getSelectedItemPosition());
        e.putInt("ant", spAnt.getSelectedItemPosition());
        e.putInt("hpf", spHpf.getSelectedItemPosition());
        e.putInt("lpf", spLpf.getSelectedItemPosition());
        e.putInt("squelch", sbSquelch.getProgress());
        e.putInt("beep", sbBeep.getProgress());
        e.putBoolean("autoMode", swAutoMode.isChecked());
        e.putBoolean("agcSend", swAgcSend.isChecked());
        e.putBoolean("dtmf", swDtmf.isChecked());
        e.putBoolean("manualTune", swManualTune.isChecked());
        e.putBoolean("cyberScan", swCyberScan.isChecked());
        e.putBoolean("afc", swAfc.isChecked());
        e.putBoolean("deEmph", swDeEmph.isChecked());
        e.putInt("ctcss", spCtcss.getSelectedItemPosition());
        e.putInt("toneElim", sbToneElim.getProgress());
        e.putInt("scramble", sbScramble.getProgress());
        e.putInt("cwPitch", spCwPitch.getSelectedItemPosition());
        e.putInt("subStep", spSubStep.getSelectedItemPosition());
        e.putInt("scanBank", spScanBank.getSelectedItemPosition());
        e.putInt("autoStore", spAutoStore.getSelectedItemPosition());
        e.putString("searchLow", etSearchLow.getText().toString().trim());
        e.putString("searchHigh", etSearchHigh.getText().toString().trim());
        e.putInt("scanLevel", sbScanLevel.getProgress());
        e.putInt("scanDelay", sbScanDelay.getProgress());
        e.putString("memoText", etMemoText.getText().toString().trim());
        e.apply();
    }

    private void loadSavedSettings() {
        uiHandler.post(() -> {
            if (prefs.contains("vfo")) {
                spVfo.setSelection(prefs.getString("vfo", "A").equals("A") ? 0 : prefs.getString("vfo", "A").charAt(0) - 'A');
                etFreq.setText(prefs.getString("freq", "145.000"));
                spMode.setSelection(prefs.getInt("mode", 0));
                spBw.setSelection(prefs.getInt("bw", 3));
                spStep.setSelection(prefs.getInt("step", 4));
                spAtt.setSelection(prefs.getInt("att", 0));
                spAnt.setSelection(prefs.getInt("ant", 0));
                spHpf.setSelection(prefs.getInt("hpf", 0));
                spLpf.setSelection(prefs.getInt("lpf", 0));
                sbSquelch.setProgress(prefs.getInt("squelch", 128));
                tvSquelchVal.setText(String.valueOf(prefs.getInt("squelch", 128)));
                sbBeep.setProgress(prefs.getInt("beep", 100));
                tvBeepVal.setText(String.valueOf(prefs.getInt("beep", 100)));
                swAutoMode.setChecked(prefs.getBoolean("autoMode", false));
                swAgcSend.setChecked(prefs.getBoolean("agcSend", false));
                swDtmf.setChecked(prefs.getBoolean("dtmf", false));
                swManualTune.setChecked(prefs.getBoolean("manualTune", false));
                swCyberScan.setChecked(prefs.getBoolean("cyberScan", false));
                swAfc.setChecked(prefs.getBoolean("afc", false));
                swDeEmph.setChecked(prefs.getBoolean("deEmph", false));
                spCtcss.setSelection(prefs.getInt("ctcss", 0));
                sbToneElim.setProgress(prefs.getInt("toneElim", 0));
                tvToneElimVal.setText("0");
                sbScramble.setProgress(prefs.getInt("scramble", 0));
                tvScrambleVal.setText("0");
                spCwPitch.setSelection(prefs.getInt("cwPitch", 1));
                spSubStep.setSelection(prefs.getInt("subStep", 0));
                spScanBank.setSelection(prefs.getInt("scanBank", 0));
                spAutoStore.setSelection(prefs.getInt("autoStore", 0));
                etSearchLow.setText(prefs.getString("searchLow", "145.000"));
                etSearchHigh.setText(prefs.getString("searchHigh", "146.000"));
                sbScanLevel.setProgress(prefs.getInt("scanLevel", 100));
                tvScanLevelVal.setText("100");
                sbScanDelay.setProgress(prefs.getInt("scanDelay", 5));
                tvScanDelayVal.setText("0.5s");
                etMemoText.setText(prefs.getString("memoText", ""));
                log("Settings loaded from prefs");
            }
        });
    }

    // ===== CONNECTION SETTINGS =====

    private void loadConnectionSettings() {
        SharedPreferences cp = getSharedPreferences(CONN_PREFS, MODE_PRIVATE);
        spTransportType.setSelection(cp.getInt("transportType", 0));
        etIp.setText(cp.getString("ip", "192.168.1.100"));
        etPort.setText(cp.getString("port", "2323"));
        spBaud.setSelection(cp.getInt("baud", 1));
    }

    private void saveConnectionSettings() {
        SharedPreferences.Editor e = getSharedPreferences(CONN_PREFS, MODE_PRIVATE).edit();
        e.putInt("transportType", spTransportType.getSelectedItemPosition());
        e.putString("ip", etIp.getText().toString().trim());
        e.putString("port", etPort.getText().toString().trim());
        e.putInt("baud", spBaud.getSelectedItemPosition());
        e.apply();
    }

    // ===== EXPORT/IMPORT =====

    private void exportSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            ioExecutor.execute(() -> {
                try {
                    JSONObject json = new JSONObject();
                    json.put("vfo", spVfo.getSelectedItem().toString());
                    json.put("freq", etFreq.getText().toString().trim());
                    json.put("mode", spMode.getSelectedItemPosition());
                    json.put("bw", spBw.getSelectedItemPosition());
                    json.put("step", spStep.getSelectedItemPosition());
                    json.put("att", spAtt.getSelectedItemPosition());
                    json.put("ant", spAnt.getSelectedItemPosition());
                    json.put("hpf", spHpf.getSelectedItemPosition());
                    json.put("lpf", spLpf.getSelectedItemPosition());
                    json.put("squelch", sbSquelch.getProgress());
                    json.put("beep", sbBeep.getProgress());
                    json.put("autoMode", swAutoMode.isChecked());
                    json.put("agcSend", swAgcSend.isChecked());
                    json.put("dtmf", swDtmf.isChecked());
                    json.put("manualTune", swManualTune.isChecked());
                    json.put("cyberScan", swCyberScan.isChecked());
                    json.put("afc", swAfc.isChecked());
                    json.put("deEmph", swDeEmph.isChecked());
                    json.put("ctcss", spCtcss.getSelectedItemPosition());
                    json.put("toneElim", sbToneElim.getProgress());
                    json.put("scramble", sbScramble.getProgress());
                    json.put("cwPitch", spCwPitch.getSelectedItemPosition());
                    json.put("subStep", spSubStep.getSelectedItemPosition());
                    json.put("scanBank", spScanBank.getSelectedItemPosition());
                    json.put("autoStore", spAutoStore.getSelectedItemPosition());
                    json.put("searchLow", etSearchLow.getText().toString().trim());
                    json.put("searchHigh", etSearchHigh.getText().toString().trim());
                    json.put("scanLevel", sbScanLevel.getProgress());
                    json.put("scanDelay", sbScanDelay.getProgress());
                    json.put("memoText", etMemoText.getText().toString().trim());
                    json.put("exportTime", new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(new Date()));

                    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                    String filename = "AR5000_settings_" + timestamp + ".json";

                    File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    File ar5000Dir = new File(downloads, "AR5000");
                    if (!ar5000Dir.exists()) ar5000Dir.mkdirs();
                    File outFile = new File(ar5000Dir, filename);

                    try (FileWriter fw = new FileWriter(outFile)) {
                        fw.write(json.toString(2));
                    }

                    uiHandler.post(() -> {
                        log("Exported: " + filename);
                        Toast.makeText(this, "Exported to Downloads/AR5000/" + filename, Toast.LENGTH_LONG).show();
                    });
                } catch (JSONException | IOException e) {
                    uiHandler.post(() -> {
                        log("Export error: " + e.getMessage());
                        Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    private void importSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("application/json");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Select settings file"), 1001);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            ioExecutor.execute(() -> {
                try (InputStream is = getContentResolver().openInputStream(uri);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);

                    JSONObject json = new JSONObject(sb.toString());
                    uiHandler.post(() -> applyJsonToUi(json));
                    log("Settings imported");
                    Toast.makeText(this, "Settings imported", Toast.LENGTH_SHORT).show();

                } catch (IOException | JSONException e) {
                    uiHandler.post(() -> {
                        log("Import error: " + e.getMessage());
                        Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    private void applyJsonToUi(JSONObject json) {
        try {
            if (json.has("vfo")) spVfo.setSelection(json.getString("vfo").charAt(0) - 'A');
            if (json.has("freq")) etFreq.setText(json.getString("freq"));
            if (json.has("mode")) spMode.setSelection(json.getInt("mode"));
            if (json.has("bw")) spBw.setSelection(json.getInt("bw"));
            if (json.has("step")) spStep.setSelection(json.getInt("step"));
            if (json.has("att")) spAtt.setSelection(json.getInt("att"));
            if (json.has("ant")) spAnt.setSelection(json.getInt("ant"));
            if (json.has("hpf")) spHpf.setSelection(json.getInt("hpf"));
            if (json.has("lpf")) spLpf.setSelection(json.getInt("lpf"));
            if (json.has("squelch")) { sbSquelch.setProgress(json.getInt("squelch")); tvSquelchVal.setText(String.valueOf(json.getInt("squelch"))); }
            if (json.has("beep")) { sbBeep.setProgress(json.getInt("beep")); tvBeepVal.setText(String.valueOf(json.getInt("beep"))); }
            if (json.has("autoMode")) swAutoMode.setChecked(json.getBoolean("autoMode"));
            if (json.has("agcSend")) swAgcSend.setChecked(json.getBoolean("agcSend"));
            if (json.has("dtmf")) swDtmf.setChecked(json.getBoolean("dtmf"));
            if (json.has("manualTune")) swManualTune.setChecked(json.getBoolean("manualTune"));
            if (json.has("cyberScan")) swCyberScan.setChecked(json.getBoolean("cyberScan"));
            if (json.has("afc")) swAfc.setChecked(json.getBoolean("afc"));
            if (json.has("deEmph")) swDeEmph.setChecked(json.getBoolean("deEmph"));
            if (json.has("ctcss")) spCtcss.setSelection(json.getInt("ctcss"));
            if (json.has("toneElim")) { sbToneElim.setProgress(json.getInt("toneElim")); tvToneElimVal.setText(String.valueOf(json.getInt("toneElim"))); }
            if (json.has("scramble")) { sbScramble.setProgress(json.getInt("scramble")); tvScrambleVal.setText(String.valueOf(json.getInt("scramble"))); }
            if (json.has("cwPitch")) spCwPitch.setSelection(json.getInt("cwPitch"));
            if (json.has("subStep")) spSubStep.setSelection(json.getInt("subStep"));
            if (json.has("scanBank")) spScanBank.setSelection(json.getInt("scanBank"));
            if (json.has("autoStore")) spAutoStore.setSelection(json.getInt("autoStore"));
            if (json.has("searchLow")) etSearchLow.setText(json.getString("searchLow"));
            if (json.has("searchHigh")) etSearchHigh.setText(json.getString("searchHigh"));
            if (json.has("scanLevel")) { sbScanLevel.setProgress(json.getInt("scanLevel")); tvScanLevelVal.setText(String.valueOf(json.getInt("scanLevel"))); }
            if (json.has("scanDelay")) { sbScanDelay.setProgress(json.getInt("scanDelay")); tvScanDelayVal.setText(String.valueOf(json.getInt("scanDelay"))); }
            if (json.has("memoText")) etMemoText.setText(json.getString("memoText"));
            saveSettings();
        } catch (JSONException e) {
            log("Apply JSON error: " + e.getMessage());
        }
    }

    // ===== SYNC UI WITH LIVE STATE =====

    private void syncUiWithState() {
        if (state == null) return;
        uiHandler.post(() -> {
            String vfo = state.getVfo();
            spVfo.setSelection(vfo.charAt(0) - 'A');
            etFreq.setText(String.format("%.3f", state.getFrequencyHz() / 1_000_000.0));
            spMode.setSelection(state.getModeCode());
            spBw.setSelection(state.getBwCode());
            long step = state.getStepHz();
            for (int i = 0; i < stepAdapter.getCount(); i++) {
                long preset = Long.parseLong(stepAdapter.getItem(i).replace("Hz", "").replace("K", "000"));
                if (preset == step) { spStep.setSelection(i); break; }
            }
            spAtt.setSelection(state.getAttenuator());
            spAnt.setSelection(state.getAntenna());
            spHpf.setSelection(state.getHpfCode());
            spLpf.setSelection(state.getLpfCode());
            sbSquelch.setProgress(state.getSquelchLevel());
            tvSquelchVal.setText(String.valueOf(state.getSquelchLevel()));
            swAutoMode.setChecked(state.isAutoMode());
        });
    }

    private void applyAllSettings() {
        if (transport == null || !transport.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        btnApplyAll.setEnabled(false);
        btnApplyAll.setText("Applying...");
        ioExecutor.execute(() -> {
            String vfo = spVfo.getSelectedItem().toString();
            try {
                double freqMhz = Double.parseDouble(etFreq.getText().toString().trim());
                sendCommandDirect(CommandFactory.setFrequency(vfo, (long)(freqMhz * 1e6)));
            } catch (Exception ignored) {}
            sendCommandDirect(CommandFactory.setMode(spMode.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setBandwidth(spBw.getSelectedItemPosition()));
            long step = Long.parseLong(stepAdapter.getItem(spStep.getSelectedItemPosition()).replace("Hz", "").replace("K", "000"));
            sendCommandDirect(CommandFactory.setStep(step));
            sendCommandDirect(CommandFactory.setAttenuator(spAtt.getSelectedItemPosition()));
            String ant = spAnt.getSelectedItemPosition() == 0 ? "AUTO" : String.valueOf(spAnt.getSelectedItemPosition());
            sendCommandDirect(CommandFactory.setAntenna(ant));
            sendCommandDirect(CommandFactory.setHpf(spHpf.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setLpf(spLpf.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setSquelch(sbSquelch.getProgress()));
            sendCommandDirect(CommandFactory.setBeep(sbBeep.getProgress()));
            sendCommandDirect(CommandFactory.setAutoMode(swAutoMode.isChecked()));
            sendCommandDirect(CommandFactory.setAgcLevelSend(swAgcSend.isChecked()));
            sendCommandDirect(CommandFactory.setDtmf(swDtmf.isChecked()));
            sendCommandDirect(CommandFactory.setManualTune(swManualTune.isChecked()));
            sendCommandDirect(CommandFactory.setCyberScan(swCyberScan.isChecked()));
            sendCommandDirect(CommandFactory.setAfc(swAfc.isChecked()));
            sendCommandDirect(CommandFactory.setDeEmphasis(swDeEmph.isChecked() ? 1 : 0));
            sendCommandDirect(CommandFactory.setCtcss(spCtcss.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setToneElim(sbToneElim.getProgress()));
            sendCommandDirect(CommandFactory.setScramble(sbScramble.getProgress()));
            sendCommandDirect(CommandFactory.setCwPitch(spCwPitch.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setSubStep(spSubStep.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setSearchBank(spScanBank.getSelectedItemPosition()));
            sendCommandDirect(CommandFactory.setLevelScan(sbScanLevel.getProgress()));
            sendCommandDirect(CommandFactory.setSearchDelay(sbScanDelay.getProgress() / 10f));
            sendCommandDirect(CommandFactory.setAutoStore(spAutoStore.getSelectedItemPosition()));
            uiHandler.post(() -> {
                btnApplyAll.setEnabled(true);
                btnApplyAll.setText("Apply All Settings");
                Toast.makeText(this, "All settings applied", Toast.LENGTH_SHORT).show();
                log("=== All settings applied ===");
                saveSettings();
            });
        });
    }

    private void sendCommand(Object cmdObj) {
        if (cmdObj instanceof com.ar5000.core.protocol.Ar5000Command) {
            sendCommandDirect((com.ar5000.core.protocol.Ar5000Command) cmdObj);
        }
    }

    private void sendCommandDirect(com.ar5000.core.protocol.Ar5000Command cmd) {
        if (transport == null || !transport.isConnected() || cmd == null) return;
        ioExecutor.execute(() -> {
            try {
                transport.write(cmd.buildPacket());
            } catch (java.io.IOException e) {
                log("TX error: " + e.getMessage());
            }
        });
    }

    private void updateConnStatus() {
        uiHandler.post(() -> {
            boolean connected = transport != null && transport.isConnected();
            tvConnStatus.setText(connected ? "Connected" : "Disconnected");
            tvConnStatus.setTextColor(connected ? 0xFF00aa00 : 0xFFaa0000);
        });
    }

    private void log(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        uiHandler.post(() -> {
            tvLog.append("[" + ts + "] " + msg + "\n");
            tvLog.post(() -> tvLog.scrollTo(0, tvLog.getBottom()));
        });
        android.util.Log.d(TAG, msg);
    }

    private void appendLuaLog(String msg) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        uiHandler.post(() -> {
            tvLuaLog.append("[" + ts + "] " + msg + "\n");
            tvLuaLog.post(() -> tvLuaLog.scrollTo(0, tvLuaLog.getBottom()));
        });
    }

    // ===== LUA INTEGRATION =====

    private void initLuaEngine() {
        try {
            radioApi = new RadioApi(transport, null, null, null, null);
            luaEngine = new ScriptEngine(radioApi);
            log("Lua engine initialized");
        } catch (Exception e) {
            log("Lua init failed: " + e.getMessage());
        }
    }

    private void scanLuaScripts() {
        luaFileList.clear();
        File dir = getFilesDir();
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles((f, name) -> name.endsWith(".lua"));
            if (files != null) {
                for (File f : files) luaFileList.add(f.getName());
            }
        }
        File extDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AR5000/scripts");
        if (extDir.exists() && extDir.isDirectory()) {
            File[] extFiles = extDir.listFiles((f, name) -> name.endsWith(".lua"));
            if (extFiles != null) {
                for (File f : extFiles) {
                    if (!luaFileList.contains(f.getName())) luaFileList.add(f.getName());
                }
            }
        }
        luaAdapter.clear();
        luaAdapter.addAll(luaFileList);
        luaAdapter.notifyDataSetChanged();
    }

    private String getLuaScriptPath(String filename) {
        File internal = new File(getFilesDir(), filename);
        if (internal.exists()) return internal.getAbsolutePath();
        File external = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "AR5000/scripts/" + filename);
        if (external.exists()) return external.getAbsolutePath();
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
        if (luaEngine != null) luaEngine.stop();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (btnExport != null && btnExport.isPressed()) {
                    exportSettings();
                } else if (btnImport != null && btnImport.isPressed()) {
                    importSettings();
                }
            } else {
                Toast.makeText(this, "Permission denied for file access", Toast.LENGTH_SHORT).show();
            }
        }
    }
}