// MainActivity.java
package com.ar5000.app;

import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.ar5000.audio.AudioRecorder;
import com.ar5000.audio.AudioPreset;
import com.ar5000.audio.SpectrumView;
import com.ar5000.core.model.ReceiverState;
import com.ar5000.core.monitor.PriorityMonitor;
import com.ar5000.core.panel.VirtualPanelController;
import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.protocol.Ar5000Protocol;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.transport.IpTransport;
import com.ar5000.core.transport.UsbSerialTransport;
import com.ar5000.core.vfo.VfoManager;
import com.ar5000.script.RadioApi;
import com.ar5000.script.ScriptEngine;
import java.io.File;
import java.util.HashMap;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import android.view.ViewGroup;
import android.widget.ScrollView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public class MainActivity extends AppCompatActivity implements Ar5000Controller.ResponseListener {

    public static Transport transport;
    public static Ar5000Controller controllerInstance;

    private Ar5000Controller controller;
    private VfoManager vfoMgr;
    private PriorityMonitor prio;
    private VirtualPanelController panel;
    private AudioRecorder recorder;
    private SpectrumView spectrum;
    private RadioApi api;
    private ScriptEngine engine;

    // === LCD & Status Elements ===
    private TextView statusBar, lcdFreq, lcdMode, lcdBw, lcdStep, lcdSignal;
    private TextView indBusy, indAnt, indKey, sqlValue;

    // === Knobs Panel ===
    private SeekBar knobAfGain, knobSquelch, knobMainDial, knobSubDial;
    private TextView txtAfGain, txtKnobSql, txtKnobMain, txtKnobSub;

    private SeekBar squelchSlider, sliderRfGain, sliderIfShift;
    private TextView txtRfGain, txtIfShift;

    private Button btnRec, btnLua, btnTerminal, btnConnect;
    private ImageButton btnSettings;
    private Spinner spinnerAnt;

    private Button btnMemory, btnSearch;
    private Button btnStep, btnAtt, btnNb, btnAgc, btnHpf;
    private Button btnLpf, btnCtcss, btnDcs, btnOffset, btnTxt;

    private final EditText[] vfoFreq = new EditText[5];
    private final Spinner[] vfoMode = new Spinner[5];
    private final Spinner[] vfoBw = new Spinner[5];
    private final Button[] vfoSet = new Button[5];

    private Button btnModeMemory, btnModeScan, btnModeSearch;
    private View panelMemory, panelScan, panelSearch;

    private Spinner spinnerMemBank, spinnerMemCh;
    private Button btnMemRead, btnMemWrite, btnMemDelete, btnMemText;

    private Spinner spinnerScanBank;
    private Button btnScanStart, btnScanStop, btnScanPass, btnScanLink;

    private Button btnSearchStart, btnSearchStop, btnSearchPass, btnSearchBank;

    private static final String[] MODE_ITEMS = Ar5000Protocol.MODE_NAMES;
    private static final String[] BW_ITEMS = Ar5000Protocol.BW_NAMES;
    private static final String[] ANT_ITEMS = {"ANT 1", "ANT 2", "ANT 3", "ANT 4", "AUTO"};
    private static final String[] MEM_BANK_ITEMS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};
    private static final String[] SCAN_BANK_ITEMS = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9"};

    private long currentFreq = 145_000_000;
    private int currentMode = Ar5000Protocol.MODE_FM;
    private int currentBw = Ar5000Protocol.BW_15K;
    private boolean isRecording = false;

    private int currentStepIdx = 0, currentAtt = 0, currentNb = 0, currentAgc = 0;
    private int currentHpf = 0, currentLpf = 0, currentCtcss = 0, currentDcs = 0;

    private static final String ACTION_USB_PERMISSION = "com.ar5000.app.USB_PERMISSION";
    private BroadcastReceiver usbReceiver;
    private UsbDevice pendingUsbDevice;

    private volatile boolean isUsbReady = false;
    private long lastErrorTime = 0;
    private String lastErrorMessage = "";

    private final AtomicReference<TextView> terminalOutputRef = new AtomicReference<>(null);

    // ===== PERIODIC POLLING =====
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private final Runnable pollRunnable = new Runnable() {
        @Override public void run() {
            if (controller != null && transport != null && transport.isConnected()) {
                controller.syncState();
            }
            pollHandler.postDelayed(this, 1500); // Poll every 1.5 seconds
        }
    };

    private void startPolling() {
        pollHandler.removeCallbacks(pollRunnable);
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        pollHandler.removeCallbacks(pollRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        registerUsbReceiver();

        SharedPreferences prefs = getSharedPreferences("ar5000_prefs", MODE_PRIVATE);
        String ip = prefs.getString("ip", "192.168.1.100");
        int port = Integer.parseInt(prefs.getString("port", "2323"));
        boolean isIp = false;

        if (isIp) { transport = new IpTransport(ip, port); }
        else { transport = new UsbSerialTransport((UsbManager) getSystemService(Context.USB_SERVICE)); }

        android.util.Log.i("CONN-MODE", "Transport created: " +
                (transport instanceof UsbSerialTransport ? "USB Serial" :
                        transport instanceof IpTransport ? "IP/WiFi" : "UNKNOWN"));

        controller = new Ar5000Controller(transport);
        controller.setResponseListener(this);
        controllerInstance = controller;

        initViews();
        initSpinners();
        setupListeners();
        initCore(transport);
        updateDisplay();

        new Thread(() -> {
            try {
                Thread.sleep(3000);
                if (transport != null && transport.isConnected()) {
                    Log.i("MANUAL-TEST", "Sending: VR\\r\\n");
                    transport.write("VR\r\n".getBytes(StandardCharsets.US_ASCII));
                }
            } catch (Exception e) { Log.e("MANUAL-TEST", "Failed", e); }
        }).start();
    }

    private void registerUsbReceiver() {
        usbReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    UsbDevice device = pendingUsbDevice;
                    if (device == null) {
                        runOnUiThread(() -> {
                            if (statusBar != null) statusBar.setText("USB ERROR");
                            Toast.makeText(MainActivity.this, "USB device not found", Toast.LENGTH_LONG).show();
                        });
                        return;
                    }
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        runOnUiThread(() -> {
                            if (transport.connect()) {
                                isUsbReady = true;
                                if (statusBar != null) { statusBar.setText("CONNECTED"); statusBar.setTextColor(0xFF00ff00); }
                                if (btnConnect != null) btnConnect.setText("Disconnect");
                                Toast.makeText(MainActivity.this, "USB connected", Toast.LENGTH_SHORT).show();
                                if (controller != null) {
                                    controller.startListening();
                                    startPolling(); // Start periodic polling
                                    new Thread(() -> { try { Thread.sleep(200); } catch (InterruptedException ignored) {} controller.syncState(); }).start();
                                }
                            } else { if (statusBar != null) statusBar.setText("CONNECT FAILED"); }
                        });
                    } else {
                        runOnUiThread(() -> { if (statusBar != null) statusBar.setText("PERMISSION DENIED"); });
                    }
                    pendingUsbDevice = null;
                }
            }
        };
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_EXPORTED);
    }

    private void logUsbDevices() {
        UsbManager usbMgr = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbMgr == null) return;
        HashMap<String, UsbDevice> devices = usbMgr.getDeviceList();
        for (UsbDevice dev : devices.values()) {
            String name = dev.getDeviceName();
            int vid = dev.getVendorId();
            boolean hasPerm = usbMgr.hasPermission(dev);
            if (name != null && (name.contains("LAN") || name.contains("Ethernet"))) continue;
            if ((vid == 0x0403 || vid == 0x1A86 || vid == 0x10C4) && !hasPerm) { requestUsbPermission(dev); break; }
            else if (hasPerm) {
                runOnUiThread(() -> {
                    if (transport.connect()) {
                        isUsbReady = true;
                        if (statusBar != null) { statusBar.setText("CONNECTED"); statusBar.setTextColor(0xFF00ff00); }
                        if (btnConnect != null) btnConnect.setText("Disconnect");
                        if (controller != null) {
                            controller.startListening();
                            startPolling(); // Start periodic polling
                            controller.syncState();
                        }
                    }
                });
                break;
            }
        }
    }

    private void requestUsbPermission(UsbDevice device) {
        UsbManager usbMgr = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (usbMgr == null) return;
        pendingUsbDevice = device;
        if (usbMgr.hasPermission(device)) {
            if (transport.connect()) {
                isUsbReady = true;
                runOnUiThread(() -> {
                    if (statusBar != null) { statusBar.setText("CONNECTED"); statusBar.setTextColor(0xFF00ff00); }
                    if (controller != null) {
                        controller.startListening();
                        startPolling(); // Start periodic polling
                        controller.syncState();
                    }
                });
            }
            return;
        }
        Intent intent = new Intent(ACTION_USB_PERMISSION);
        intent.setPackage(getPackageName());
        usbMgr.requestPermission(device, android.app.PendingIntent.getBroadcast(this, 0, intent, android.app.PendingIntent.FLAG_MUTABLE));
    }

    private void safeSend(Runnable action) {
        if (isUsbReady && transport != null && transport.isConnected() && controller != null) action.run();
    }

    private void initViews() {
        statusBar = findViewById(R.id.statusBar);
        lcdFreq = findViewById(R.id.lcdFreq);
        lcdMode = findViewById(R.id.lcdMode);
        lcdBw = findViewById(R.id.lcdBw);
        lcdStep = findViewById(R.id.lcdStep);
        lcdSignal = findViewById(R.id.lcdSignal);
        indBusy = findViewById(R.id.indBusy);
        indAnt = findViewById(R.id.indAnt);
        indKey = findViewById(R.id.indKey);
        sqlValue = findViewById(R.id.sqlValue);

        // Knobs
//        knobAfGain = findViewById(R.id.knobAfGain);
//        knobSquelch = findViewById(R.id.knobSquelch);
//        knobMainDial = findViewById(R.id.knobMainDial);
//        knobSubDial = findViewById(R.id.knobSubDial);
//        txtAfGain = findViewById(R.id.txtAfGain);
//        txtKnobSql = findViewById(R.id.txtKnobSql);
//        txtKnobMain = findViewById(R.id.txtKnobMain);
//        txtKnobSub = findViewById(R.id.txtKnobSub);

        squelchSlider = findViewById(R.id.squelchSlider);
        sliderRfGain = findViewById(R.id.sliderRfGain);
        txtRfGain = findViewById(R.id.txtRfGain);
        sliderIfShift = findViewById(R.id.sliderIfShift);
        txtIfShift = findViewById(R.id.txtIfShift);

        btnRec = findViewById(R.id.btnRec);
        btnLua = findViewById(R.id.btnLua);
        btnTerminal = findViewById(R.id.btnTerminal);
        btnConnect = findViewById(R.id.btnConnect);
        btnSettings = findViewById(R.id.btnSettings);
        spinnerAnt = findViewById(R.id.spinnerAnt);

        btnStep = findViewById(R.id.btnStep); btnAtt = findViewById(R.id.btnAtt); btnNb = findViewById(R.id.btnNb);
        btnAgc = findViewById(R.id.btnAgc); btnHpf = findViewById(R.id.btnHpf); btnLpf = findViewById(R.id.btnLpf);
        btnCtcss = findViewById(R.id.btnCtcss); btnDcs = findViewById(R.id.btnDcs); btnOffset = findViewById(R.id.btnOffset); btnTxt = findViewById(R.id.btnTxt);

        btnModeMemory = findViewById(R.id.btnModeMemory); btnModeScan = findViewById(R.id.btnModeScan); btnModeSearch = findViewById(R.id.btnModeSearch);
        panelMemory = findViewById(R.id.panelMemory); panelScan = findViewById(R.id.panelScan); panelSearch = findViewById(R.id.panelSearch);

        spinnerMemBank = findViewById(R.id.spinnerMemBank); spinnerMemCh = findViewById(R.id.spinnerMemCh);
        btnMemRead = findViewById(R.id.btnMemRead); btnMemWrite = findViewById(R.id.btnMemWrite); btnMemDelete = findViewById(R.id.btnMemDelete);

        spinnerScanBank = findViewById(R.id.spinnerScanBank);
        btnScanStart = findViewById(R.id.btnScanStart); btnScanPass = findViewById(R.id.btnScanPass); btnScanLink = findViewById(R.id.btnScanLink);

        btnSearchStart = findViewById(R.id.btnSearchStart); btnSearchPass = findViewById(R.id.btnSearchPass); btnSearchBank = findViewById(R.id.btnSearchBank);

        vfoFreq[0] = findViewById(R.id.freqA); vfoMode[0] = findViewById(R.id.modeA); vfoBw[0] = findViewById(R.id.bwA); vfoSet[0] = findViewById(R.id.btnSetA);
        vfoFreq[1] = findViewById(R.id.freqB); vfoMode[1] = findViewById(R.id.modeB); vfoBw[1] = findViewById(R.id.bwB); vfoSet[1] = findViewById(R.id.btnSetB);
        vfoFreq[2] = findViewById(R.id.freqC); vfoMode[2] = findViewById(R.id.modeC); vfoBw[2] = findViewById(R.id.bwC); vfoSet[2] = findViewById(R.id.btnSetC);
        vfoFreq[3] = findViewById(R.id.freqD); vfoMode[3] = findViewById(R.id.modeD); vfoBw[3] = findViewById(R.id.bwD); vfoSet[3] = findViewById(R.id.btnSetD);
        vfoFreq[4] = findViewById(R.id.freqE); vfoMode[4] = findViewById(R.id.modeE); vfoBw[4] = findViewById(R.id.bwE); vfoSet[4] = findViewById(R.id.btnSetE);
    }

    private void initSpinners() {
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MODE_ITEMS);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Spinner s : vfoMode) if (s != null) { s.setAdapter(modeAdapter); s.setSelection(0); }

        ArrayAdapter<String> bwAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, BW_ITEMS);
        bwAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        for (Spinner s : vfoBw) if (s != null) { s.setAdapter(bwAdapter); s.setSelection(0); }

        ArrayAdapter<String> antAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ANT_ITEMS);
        antAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerAnt != null) { spinnerAnt.setAdapter(antAdapter); spinnerAnt.setSelection(0); }

        ArrayAdapter<String> memBankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, MEM_BANK_ITEMS);
        memBankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerMemBank != null) { spinnerMemBank.setAdapter(memBankAdapter); spinnerMemBank.setSelection(0); }

        String[] chItems = new String[100];
        for (int i = 0; i < 100; i++) chItems[i] = String.format("%02d", i);
        ArrayAdapter<String> memChAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, chItems);
        memChAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerMemCh != null) { spinnerMemCh.setAdapter(memChAdapter); spinnerMemCh.setSelection(0); }

        ArrayAdapter<String> scanBankAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, SCAN_BANK_ITEMS);
        scanBankAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        if (spinnerScanBank != null) { spinnerScanBank.setAdapter(scanBankAdapter); spinnerScanBank.setSelection(0); }
    }

    private void initCore(Transport transport) {
        vfoMgr = new VfoManager();
        recorder = new AudioRecorder();
        spectrum = findViewById(R.id.spectrum);
        spectrum.setCenter(currentFreq); spectrum.setSpan(24_000f);

        panel = new VirtualPanelController();
        panel.attachTransport(transport);
        panel.setListener(new VirtualPanelController.UpdateListener() {
            @Override public void onStateChanged(ReceiverState s) { runOnUiThread(() -> updateDisplay(s)); }
            @Override public void onCmdSent(String h, String p) { runOnUiThread(() -> { if (statusBar != null) statusBar.setText("CMD: " + h); }); }
            @Override public void onLog(String m) { runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show()); }
        });

        prio = new PriorityMonitor(vfoMgr, controller);
        api = new RadioApi(transport, vfoMgr, prio, recorder, spectrum);

        if (transport instanceof UsbSerialTransport) logUsbDevices();
        else {
            new Thread(() -> {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                if (transport.connect()) {
                    isUsbReady = true;
                    runOnUiThread(() -> {
                        if (statusBar != null) { statusBar.setText("CONNECTED"); statusBar.setTextColor(0xFF00ff00); }
                        if (controller != null) { controller.startListening(); startPolling(); controller.syncState(); }
                    });
                }
            }).start();
        }
    }

    private void setupListeners() {
        if (btnSettings != null) btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        if (btnLua != null) btnLua.setOnClickListener(v -> showLuaDialog());
        if (btnTerminal != null) btnTerminal.setOnClickListener(v -> showTerminalDialog());

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> {
                if (transport != null && transport.isConnected()) {
                    transport.disconnect(); isUsbReady = false; stopPolling();
                    if (statusBar != null) { statusBar.setText("DISCONNECTED"); statusBar.setTextColor(0xFF888888); }
                    btnConnect.setText("Connect");
                } else {
                    if (transport instanceof UsbSerialTransport) logUsbDevices();
                    else if (transport.connect()) {
                        isUsbReady = true;
                        if (statusBar != null) { statusBar.setText("CONNECTED"); statusBar.setTextColor(0xFF00ff00); }
                        btnConnect.setText("Disconnect");
                        if (controller != null) { controller.startListening(); startPolling(); controller.syncState(); }
                    }
                }
            });
        }

        if (btnRec != null) {
            btnRec.setOnClickListener(v -> {
                isRecording = !isRecording;
                if (isRecording) {
                    btnRec.setText("STOP"); btnRec.setBackgroundColor(0xFFff0000);
                    File dir = getExternalFilesDir("Recordings");
                    if (dir != null && !dir.exists()) dir.mkdirs();
                    recorder.setPreset(AudioPreset.standard()); recorder.start(dir);
                } else {
                    btnRec.setText("REC"); btnRec.setBackgroundColor(0xFF00aa00); recorder.stop();
                }
            });
        }

        if (squelchSlider != null) {
            squelchSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean from) { if (from) { if (sqlValue != null) sqlValue.setText(String.valueOf(p)); safeSend(() -> controller.setSquelch(p)); } }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        if (sliderRfGain != null && txtRfGain != null) {
            sliderRfGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int val, boolean from) { if (from) { txtRfGain.setText(String.valueOf(val)); safeSend(() -> controller.setRfGain(val)); } }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        if (sliderIfShift != null && txtIfShift != null) {
            sliderIfShift.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int val, boolean from) { if (from) { int shift = val - 3000; txtIfShift.setText(shift + " Hz"); safeSend(() -> controller.setIfShift(shift)); } }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        // ===== KNOBS LISTENERS =====
        if (knobAfGain != null && txtAfGain != null) {
            knobAfGain.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean from) { if (from) txtAfGain.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }
        if (knobSquelch != null && txtKnobSql != null) {
            knobSquelch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean from) { if (from) txtKnobSql.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) { safeSend(() -> controller.setSquelch(sb.getProgress())); }
            });
        }
        if (knobMainDial != null && txtKnobMain != null) {
            knobMainDial.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean from) { if (from) txtKnobMain.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    // Map to Step (ST) command: 0-100 -> 1K-100K
                    long step = 1000 + (sb.getProgress() * 990L);
                    safeSend(() -> controller.setStep(step));
                }
            });
        }
        if (knobSubDial != null && txtKnobSub != null) {
            knobSubDial.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean from) { if (from) txtKnobSub.setText(String.valueOf(p)); }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {
                    // Map to Attenuator (AT): 0-3 -> 0dB, 10dB, 20dB, AUTO
                    safeSend(() -> controller.setAttenuator(sb.getProgress() > 3 ? 3 : sb.getProgress()));
                }
            });
        }

        if (spinnerAnt != null) {
            spinnerAnt.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int pos, long id) {
                    String antName = ANT_ITEMS[pos];
                    if (transport == null || !transport.isConnected()) return;
                    try {
                        int antParam = antName.equals("AUTO") ? 0 : Integer.parseInt(antName.replace("ANT ", ""));
                        controller.setAntenna(antParam);
                    } catch (Exception ignored) {}
                }
                @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            });
        }

        if (btnModeMemory != null) btnModeMemory.setOnClickListener(v -> { if (panelMemory != null) panelMemory.setVisibility(View.VISIBLE); if (panelScan != null) panelScan.setVisibility(View.GONE); if (panelSearch != null) panelSearch.setVisibility(View.GONE); });
        if (btnModeScan != null) btnModeScan.setOnClickListener(v -> { if (panelMemory != null) panelMemory.setVisibility(View.GONE); if (panelScan != null) panelScan.setVisibility(View.VISIBLE); if (panelSearch != null) panelSearch.setVisibility(View.GONE); });
        if (btnModeSearch != null) btnModeSearch.setOnClickListener(v -> { if (panelMemory != null) panelMemory.setVisibility(View.GONE); if (panelScan != null) panelScan.setVisibility(View.GONE); if (panelSearch != null) panelSearch.setVisibility(View.VISIBLE); });

        if (btnMemRead != null) btnMemRead.setOnClickListener(v -> { int b = spinnerMemBank != null ? Integer.parseInt(spinnerMemBank.getSelectedItem().toString()) : 0; int c = spinnerMemCh != null ? Integer.parseInt(spinnerMemCh.getSelectedItem().toString()) : 0; safeSend(() -> controller.readMemory(b, c)); });
        if (btnMemWrite != null) btnMemWrite.setOnClickListener(v -> {
            int b = spinnerMemBank != null ? Integer.parseInt(spinnerMemBank.getSelectedItem().toString()) : 0;
            int c = spinnerMemCh != null ? Integer.parseInt(spinnerMemCh.getSelectedItem().toString()) : 0;
            String f = vfoFreq[0] != null ? vfoFreq[0].getText().toString().trim() : "145.000";
            try {
                long freqHz = (long) (Double.parseDouble(f) * 1_000_000);
                String mode = vfoMode[0] != null ? vfoMode[0].getSelectedItem().toString() : "AUTO";
                String bw = vfoBw[0] != null ? vfoBw[0].getSelectedItem().toString() : "AUTO";
                safeSend(() -> controller.writeMemory(b, c, freqHz, Ar5000Protocol.getModeCode(mode), Ar5000Protocol.getBwCode(bw)));
            } catch (Exception ignored) {}
        });
        if (btnMemDelete != null) btnMemDelete.setOnClickListener(v -> { int b = spinnerMemBank != null ? Integer.parseInt(spinnerMemBank.getSelectedItem().toString()) : 0; int c = spinnerMemCh != null ? Integer.parseInt(spinnerMemCh.getSelectedItem().toString()) : 0; safeSend(() -> controller.clearMemory(b, c)); });

        if (btnScanStart != null) btnScanStart.setOnClickListener(v -> { int b = spinnerScanBank != null ? Integer.parseInt(spinnerScanBank.getSelectedItem().toString()) : 0; safeSend(() -> controller.startScan(b)); });
        if (btnScanPass != null) btnScanPass.setOnClickListener(v -> safeSend(() -> controller.setSearchLimits(currentFreq, currentFreq + 10000)));

        if (btnSearchStart != null) btnSearchStart.setOnClickListener(v -> {
            String low = vfoFreq[0] != null ? vfoFreq[0].getText().toString().trim() : "145.000";
            String high = vfoFreq[1] != null ? vfoFreq[1].getText().toString().trim() : "146.000";
            try { safeSend(() -> { controller.setSearchLimits((long)(Double.parseDouble(low)*1e6), (long)(Double.parseDouble(high)*1e6)); controller.startSearch(0); }); } catch (Exception ignored) {}
        });
        if (btnSearchPass != null) btnSearchPass.setOnClickListener(v -> safeSend(() -> controller.setSearchLimits(currentFreq, currentFreq + 10000)));

        for (int i = 0; i < 5; i++) { final int idx = i; if (vfoSet[i] != null) vfoSet[i].setOnClickListener(v -> safeSend(() -> applyVfoSettings(idx))); }

        if (btnStep != null) btnStep.setOnClickListener(v -> { long[] steps = {5000, 10000, 25000, 50000, 100000}; currentStepIdx = (currentStepIdx + 1) % steps.length; safeSend(() -> controller.setStep(steps[currentStepIdx])); });
        if (btnAtt != null) btnAtt.setOnClickListener(v -> { int[] att = {0, 1, 2}; currentAtt = (currentAtt + 1) % att.length; safeSend(() -> controller.setAttenuator(currentAtt)); });
        if (btnNb != null) btnNb.setOnClickListener(v -> { currentNb = (currentNb + 1) % 2; safeSend(() -> controller.setNoiseBlanker(currentNb)); });
        if (btnAgc != null) btnAgc.setOnClickListener(v -> { currentAgc = (currentAgc + 1) % 4; safeSend(() -> controller.setAgc(currentAgc)); });
        if (btnHpf != null) btnHpf.setOnClickListener(v -> { currentHpf = (currentHpf + 1) % 5; safeSend(() -> controller.setHpf(currentHpf)); });
        if (btnLpf != null) btnLpf.setOnClickListener(v -> { currentLpf = (currentLpf + 1) % 5; safeSend(() -> controller.setLpf(currentLpf)); });
        if (btnCtcss != null) btnCtcss.setOnClickListener(v -> { currentCtcss = (currentCtcss + 1) % 2; safeSend(() -> controller.setCtcss(currentCtcss)); });
        if (btnDcs != null) btnDcs.setOnClickListener(v -> { currentDcs = (currentDcs + 1) % 2; safeSend(() -> controller.setDcs(currentDcs)); });
        if (btnOffset != null) btnOffset.setOnClickListener(v -> safeSend(() -> controller.clearOffset()));
        if (btnTxt != null) btnTxt.setOnClickListener(v -> safeSend(() -> controller.setLcdText("AR5000")));
    }

    private void applyVfoSettings(int vfoIdx) {
        EditText freqEdit = vfoFreq[vfoIdx];
        Spinner modeSpin = vfoMode[vfoIdx];
        Spinner bwSpin = vfoBw[vfoIdx];
        if (freqEdit == null || modeSpin == null || bwSpin == null) return;
        try {
            String freqStr = freqEdit.getText().toString().trim();
            if (!freqStr.isEmpty()) {
                long freqHz = (long) (Double.parseDouble(freqStr) * 1_000_000);
                String mode = modeSpin.getSelectedItem().toString();
                String bw = bwSpin.getSelectedItem().toString();
                String vfoId = String.valueOf((char) ('A' + vfoIdx));
                controller.setFrequency(vfoId, freqHz);
                if (!"AUTO".equals(mode)) controller.setMode(Ar5000Protocol.getModeCode(mode));
                if (!"AUTO".equals(bw)) controller.setBandwidth(Ar5000Protocol.getBwCode(bw));
            }
        } catch (Exception ignored) {}
    }

    private String formatAntenna(int code) {
        switch (code) { case 0: return "AUTO"; case 1: return "1"; case 2: return "2"; case 3: return "3"; case 4: return "4"; default: return "?"; }
    }
    private String formatAtt(int code) {
        switch (code) { case 0: return "0dB"; case 1: return "10dB"; case 2: return "20dB"; case 3: return "AUTO"; default: return "OFF"; }
    }
    private String formatStep(long hz) { return (hz >= 1000) ? (hz / 1000) + "K" : hz + "Hz"; }

    private void updateDisplay() { updateDisplay(null); }
    private void updateDisplay(ReceiverState state) {
        if (state != null) { currentFreq = state.getFrequencyHz(); currentMode = state.getModeCode(); currentBw = state.getBwCode(); }
        long mhz = currentFreq / 1_000_000;
        long khz = (currentFreq / 1000) % 1000;
        long hz = currentFreq % 1000;
        if (lcdFreq != null) lcdFreq.setText(String.format("%d.%03d.%03d", mhz, khz, hz));
        if (lcdMode != null) lcdMode.setText(Ar5000Protocol.getModeName(currentMode));
        if (lcdBw != null) lcdBw.setText(Ar5000Protocol.getBwName(currentBw));
        if (state != null) {
            int s = state.getSignalStrength() / 10;
            if (lcdSignal != null) lcdSignal.setText(s > 9 ? "S9+" + (s - 9) + "dB" : "S" + s);
        } else { if (lcdSignal != null) lcdSignal.setText("S0"); }
    }

    @Override public void onFrequencyChanged(String vfo, long freqHz) { runOnUiThread(() -> { if ("VA".equals(vfo) || "VB".equals(vfo)) { currentFreq = freqHz; updateDisplay(null); } }); }
    @Override public void onModeChanged(int modeCode) { runOnUiThread(() -> { currentMode = modeCode; updateDisplay(null); }); }
    @Override public void onBandwidthChanged(int bwCode) { runOnUiThread(() -> { currentBw = bwCode; updateDisplay(null); }); }
    @Override public void onSignalStrength(int sValue) { runOnUiThread(() -> { int s = sValue / 10; if (lcdSignal != null) lcdSignal.setText(s > 9 ? "S9+" + (s - 9) + "dB" : "S" + s); }); }
    @Override public void onBusy(boolean busy) { runOnUiThread(() -> { if (indBusy != null) { indBusy.setText(busy ? "BUSY" : "----"); indBusy.setTextColor(busy ? 0xFF00ff00 : 0xFF003300); } }); }
    @Override public void onError(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorTime > 2000 || !message.equals(lastErrorMessage)) {
            lastErrorTime = now; lastErrorMessage = message;
            runOnUiThread(() -> {
                if (message != null && !message.isEmpty()) {
                    Toast.makeText(this, "ERR: " + message, Toast.LENGTH_SHORT).show();
                    if (statusBar != null) { statusBar.setText("ERR: " + message); statusBar.setTextColor(0xFFff4444); }
                }
            });
        }
    }
    @Override public void onRawStatus(String raw) { if (terminalOutputRef.get() != null) appendLogToTerminal("<- RX: [" + raw + "]"); }
    @Override public void onStateChanged(ReceiverState state) { runOnUiThread(() -> updateDisplay(state)); }

    private void showLuaDialog() { Dialog d = new Dialog(this); d.setContentView(R.layout.dialog_lua_editor); d.setTitle("Lua Script"); d.setCancelable(true); d.show(); }
    private void showAdvancedPanel() {}

    private void showTerminalDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_terminal);
        dialog.setTitle("USB Terminal");
        dialog.setCancelable(true);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        TextView output = dialog.findViewById(R.id.terminalOutput);
        ScrollView scroll = dialog.findViewById(R.id.terminalScroll);
        EditText input = dialog.findViewById(R.id.cmdInput);
        Button btnSend = dialog.findViewById(R.id.btnSend);
        Button btnVR = dialog.findViewById(R.id.btnVR);
        Button btnAG = dialog.findViewById(R.id.btnAG);
        Button btnMD = dialog.findViewById(R.id.btnMD);
        Button btnClear = dialog.findViewById(R.id.btnClear);

        terminalOutputRef.set(output);
        runOnUiThread(() -> {
            if (output != null) {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                output.append("[" + ts + "] === TERMINAL OPENED ==\n");
                output.append("[" + ts + "] Transport: " + (transport != null && transport.isConnected() ? "CONNECTED" : "DISCONNECTED") + "\n");
                if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }
        });

        java.util.function.Consumer<String> appendLog = (text) -> {
            runOnUiThread(() -> {
                String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
                if (output != null) output.append("[" + ts + "] " + text + "\n");
                if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            });
        };

        java.util.function.Consumer<String> sendCommand = (cmd) -> {
            if (transport == null || !transport.isConnected()) { appendLog.accept("ERROR: Transport not connected"); return; }
            try {
                String fullCmd = cmd.trim() + "\r\n";
                appendLog.accept("-> TX: [" + cmd.trim() + "]");
                transport.write(fullCmd.getBytes(StandardCharsets.US_ASCII));
            } catch (Exception e) { appendLog.accept("ERROR TX: " + e.getMessage()); }
        };

        if (btnSend != null) btnSend.setOnClickListener(v -> { if (input != null) { String cmd = input.getText().toString().trim(); if (!cmd.isEmpty()) { sendCommand.accept(cmd); input.setText(""); } } });
        if (input != null) input.setOnEditorActionListener((v, actionId, event) -> { if (android.view.inputmethod.EditorInfo.IME_ACTION_SEND == actionId) { if (btnSend != null) btnSend.performClick(); return true; } return false; });

        if (btnVR != null) btnVR.setOnClickListener(v -> sendCommand.accept("VR"));
        if (btnAG != null) btnAG.setOnClickListener(v -> sendCommand.accept("AG?"));
        if (btnMD != null) btnMD.setOnClickListener(v -> sendCommand.accept("MD 0"));
        if (btnClear != null) btnClear.setOnClickListener(v -> runOnUiThread(() -> { if (output != null) output.setText(""); }));

        Ar5000Controller.ResponseListener originalListener = controller.getResponseListener();
        Ar5000Controller.ResponseListener terminalListener = new Ar5000Controller.ResponseListener() {
            @Override public void onFrequencyChanged(String vfo, long freqHz) { appendLog.accept("FREQ: " + vfo + "=" + freqHz); if (originalListener != null) originalListener.onFrequencyChanged(vfo, freqHz); }
            @Override public void onModeChanged(int modeCode) { appendLog.accept("MODE: " + modeCode); if (originalListener != null) originalListener.onModeChanged(modeCode); }
            @Override public void onBandwidthChanged(int bwCode) { appendLog.accept("BW: " + bwCode); if (originalListener != null) originalListener.onBandwidthChanged(bwCode); }
            @Override public void onSignalStrength(int sValue) { appendLog.accept("SIGNAL: " + sValue); if (originalListener != null) originalListener.onSignalStrength(sValue); }
            @Override public void onBusy(boolean busy) { appendLog.accept("BUSY: " + busy); if (originalListener != null) originalListener.onBusy(busy); }
            @Override public void onError(String message) { appendLog.accept("ERR: " + message); if (originalListener != null) originalListener.onError(message); }
            @Override public void onRawStatus(String raw) { if (raw != null) { String t = raw.trim(); if (!t.isEmpty()) appendLog.accept("<- RX: [" + t + "]"); } if (originalListener != null) originalListener.onRawStatus(raw); }
            @Override public void onStateChanged(ReceiverState state) { appendLog.accept("STATE: F=" + state.getFrequencyHz() + " M=" + state.getModeCode()); if (originalListener != null) originalListener.onStateChanged(state); }
        };
        controller.setResponseListener(terminalListener);
        dialog.setOnDismissListener(d -> { controller.setResponseListener(originalListener); terminalOutputRef.set(null); });
        dialog.show();
    }

    private void appendLogToTerminal(String text) {
        TextView output = terminalOutputRef.get();
        if (output != null) output.post(() -> {
            String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
            output.append("[" + ts + "] " + text + "\n");
            ScrollView scroll = (ScrollView) output.getParent();
            if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    public static void logToTerminalDirect(String message) { Log.i("TERMINAL-DIRECT", message); }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (usbReceiver != null) unregisterReceiver(usbReceiver);
        if (controller != null) controller.shutdown();
        if (transport != null) transport.disconnect();
    }
}