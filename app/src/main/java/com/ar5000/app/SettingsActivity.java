package com.ar5000.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.ar5000.core.protocol.CommandFactory;
import java.io.IOException;

public class SettingsActivity extends AppCompatActivity {

    private static final String PREFS = "ar5000_prefs";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        initUI();
    }

    private void initUI() {
        RadioGroup rgType = findViewById(R.id.transportType);
        EditText etIp = findViewById(R.id.etIp);
        EditText etPort = findViewById(R.id.etPort);

        etIp.setText(prefs.getString("ip", "192.168.1.100"));
        etPort.setText(prefs.getString("port", "2323"));
        rgType.check(prefs.getBoolean("isIp", true) ? R.id.typeIp : R.id.typeUsb);

        // --- CONFIG MENU UI ---
        Spinner spBaud = findViewById(R.id.spinnerBaud);
        ArrayAdapter<String> baudAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"4800", "9600", "19200"});
        baudAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spBaud.setAdapter(baudAdapter);
        spBaud.setSelection(baudAdapter.getPosition(prefs.getString("baud", "9600")));

        Spinner spAnt = findViewById(R.id.spinnerAnt);
        ArrayAdapter<String> antAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"ANT 1", "ANT 2", "ANT 3", "ANT 4", "AUTO"});
        antAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spAnt.setAdapter(antAdapter);
        spAnt.setSelection(antAdapter.getPosition(prefs.getString("antenna", "ANT 1")));

        Spinner spExtIf = findViewById(R.id.spinnerExtIf);
        ArrayAdapter<String> extIfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"OFF (Default)", "1 (SDU5000 IF Out)", "2 (Audio IF Out)"});
        extIfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spExtIf.setAdapter(extIfAdapter);
        spExtIf.setSelection(prefs.getInt("extIf", 0));

        // --- OPTION MENU UI ---
        Spinner spDtmf = findViewById(R.id.spinnerDtmf);
        ArrayAdapter<String> dtmfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"OFF", "ON"});
        dtmfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spDtmf.setAdapter(dtmfAdapter);
        spDtmf.setSelection(prefs.getBoolean("dtmfOn", false) ? 1 : 0);

        SeekBar seekTE = findViewById(R.id.seekBarTE);
        TextView txtTE = findViewById(R.id.txtTEVal);
        int teVal = prefs.getInt("toneElim", 0);
        seekTE.setProgress(teVal);
        txtTE.setText(teVal == 0 ? "OFF" : String.valueOf(teVal));

        //CheckBox cbNb = findViewById(R.id.cbNoiseBlanker);
        //cbNb.setChecked(prefs.getBoolean("nbOn", false));

        Spinner spLamp = findViewById(R.id.spinnerLamp);
        ArrayAdapter<String> lampAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[]{"ON", "OFF"});
        lampAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spLamp.setAdapter(lampAdapter);
        spLamp.setSelection(prefs.getBoolean("lampOn", true) ? 0 : 1);

        SeekBar seekBeep = findViewById(R.id.seekBarBeep);
        TextView txtBeep = findViewById(R.id.txtBeepVal);
        int beepVal = prefs.getInt("beepVol", 100);
        seekBeep.setProgress(beepVal);
        txtBeep.setText(String.valueOf(beepVal));

        // --- LISTENERS (FIXED: Anonymous classes instead of lambdas) ---

        seekBeep.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int val, boolean from) {
                txtBeep.setText(String.valueOf(val));
                if(from) sendCommand(CommandFactory.setBeep(val));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        seekTE.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int val, boolean from) {
                txtTE.setText(val == 0 ? "OFF" : String.valueOf(val));
                if(from) sendCommand(CommandFactory.setToneElim(val));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        // FIX: Standard anonymous inner classes for Spinners
        spAnt.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String val = pos < 4 ? String.valueOf(pos + 1) : "AUTO";
                sendCommand(CommandFactory.setAntenna(val));
                prefs.edit().putString("antenna", spAnt.getItemAtPosition(pos).toString()).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spExtIf.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                sendCommand(CommandFactory.setExtIf(pos));
                prefs.edit().putInt("extIf", pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spBaud.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                int rate = Integer.parseInt(spBaud.getItemAtPosition(pos).toString());
                sendCommand(CommandFactory.setBaud(rate));
                prefs.edit().putString("baud", String.valueOf(rate)).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spDtmf.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean on = pos == 1;
                sendCommand(CommandFactory.setDtmf(on));
                prefs.edit().putBoolean("dtmfOn", on).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spLamp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                boolean on = pos == 0;
                sendCommand(CommandFactory.setLamp(on));
                prefs.edit().putBoolean("lampOn", on).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

/*        cbNb.setOnCheckedChangeListener((btn, isChecked) -> {
            sendCommand(CommandFactory.setNoiseBlanker(isChecked));
            prefs.edit().putBoolean("nbOn", isChecked).apply();
        });*/

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            SharedPreferences.Editor e = prefs.edit();
            e.putString("ip", etIp.getText().toString().trim());
            e.putString("port", etPort.getText().toString().trim());
            e.putBoolean("isIp", rgType.getCheckedRadioButtonId() == R.id.typeIp);
            e.apply();
            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void sendCommand(Object cmdObj) {
        if (MainActivity.transport != null && MainActivity.transport.isConnected()) {
            try {
                MainActivity.transport.write(((com.ar5000.core.protocol.Ar5000Command) cmdObj).buildPacket());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}