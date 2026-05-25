package com.ar5000.script;

import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.vfo.VfoManager;
import com.ar5000.core.monitor.PriorityMonitor;
import com.ar5000.audio.AudioPreset;
import com.ar5000.audio.AudioRecorder;
import com.ar5000.audio.SpectrumView;

import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class RadioApi {
    private static final String TAG = "RadioApi";

    private final Transport transport;
    private final VfoManager vfoMgr;
    private final PriorityMonitor prio;
    private final AudioRecorder recorder;
    private final SpectrumView spectrum;

    public RadioApi(Transport t, VfoManager v, PriorityMonitor p, AudioRecorder r, SpectrumView s) {
        transport = t;
        vfoMgr = v;
        prio = p;
        recorder = r;
        spectrum = s;
    }

    public void setPrioListener(PriorityMonitor.AlertListener l) {
        if (prio != null) prio.setListener(l);
    }

    public void setFrequency(String vfo, long freq) {
        if (transport != null && transport.isConnected()) {
            try {
                transport.write(CommandFactory.setFrequency(vfo, freq).buildPacket());
            } catch (IOException e) {
                Log.e(TAG, "setFrequency failed", e);
            }
        }
    }

    public long getFrequency(String vfo) {
        if (vfoMgr != null) {
            try {
                VfoManager.VfoId id = VfoManager.VfoId.valueOf(vfo.toUpperCase());
                return vfoMgr.getState(id).getFrequencyHz();
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid VFO name: " + vfo);
            }
        }
        return 0;
    }

    public void setMode(int m) {
        if (transport != null && transport.isConnected()) {
            try {
                transport.write(CommandFactory.setMode(m).buildPacket());
            } catch (IOException e) {
                Log.e(TAG, "setMode failed", e);
            }
        }
    }

    public int getAgcLevel() {
        if (vfoMgr != null) {
            return vfoMgr.getState(vfoMgr.getActive()).getSignalStrength();
        }
        return 0;
    }

    public boolean isRunning() { return true; }

    public void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public void log(String m) { Log.i("ScriptLog", m); }

    public void addMarker(String l) {
        if (recorder != null && recorder.isRecording()) {
            recorder.addMarker(l);
        }
    }

    public boolean startRec(int preset, String fn) {
        if (recorder == null) return false;
        File d = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC);
        if (d == null) d = new File("/sdcard/Music");
        if (!d.exists()) d.mkdirs();

        AudioPreset p = (preset == 0) ? AudioPreset.narrow() : AudioPreset.standard();
        recorder.setPreset(p);
        return recorder.start(d);
    }

    public boolean stopRec() {
        return recorder != null && recorder.stop() != null;
    }

    public boolean isRecording() {
        return recorder != null && recorder.isRecording();
    }

    public void setPreset(int id) {
        if (recorder != null) {
            AudioPreset p = (id == 0) ? AudioPreset.narrow() : AudioPreset.standard();
            recorder.setPreset(p);
        }
    }

    public void setSpectrum(float c, float s, String mode) {
        if (spectrum != null) {
            spectrum.setCenter(c);
            spectrum.setSpan(s);
            spectrum.setMode("waterfall".equals(mode));
        }
    }

    public float[] getSpectrumData() {
        return spectrum != null ? spectrum.getData() : new float[0];
    }

    public void setPriorityVfo(String vfo, int thr, boolean sw, long ms) {
        if (prio != null) {
            try {
                prio.setTarget(VfoManager.VfoId.valueOf(vfo.toUpperCase()), thr, sw, ms);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid VFO for priority: " + vfo);
            }
        }
    }

    public void startPriorityMonitor() {
        if (prio != null) prio.start();
    }

    public void stopPriorityMonitor() {
        if (prio != null) prio.stop();
    }

    public void selectVfo(String v) {
        if (vfoMgr != null) {
            try {
                vfoMgr.select(VfoManager.VfoId.valueOf(v.toUpperCase()));
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Invalid VFO selection: " + v);
            }
        }
    }

    public boolean writeMemory(int b, int c, Map<String, Object> d) {
        if (transport != null && transport.isConnected()) {
            try {
                long freq = ((Number) d.get("freq")).longValue();
                int mode = ((Number) d.get("mode")).intValue();
                int bw = ((Number) d.get("bw")).intValue();
                transport.write(CommandFactory.writeMemory(b, c, freq, mode, bw).buildPacket());
                return true;
            } catch (IOException e) {
                Log.e(TAG, "writeMemory failed", e);
            } catch (Exception e) {
                Log.e(TAG, "writeMemory data conversion error", e);
            }
        }
        return false;
    }

    public boolean clearMemory(int b, int c) {
        if (transport != null && transport.isConnected()) {
            try {
                transport.write(CommandFactory.clearMemory(b, c).buildPacket());
                return true;
            } catch (IOException e) {
                Log.e(TAG, "clearMemory failed", e);
            }
        }
        return false;
    }
    // ===== NEW LUA API METHODS =====

    // 1. Step
    public void setStep(long hz) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setStep(hz).buildPacket()); } catch(Exception e){} }

    // 2. Attenuator
    public void setAttenuator(int code) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setAttenuator(code).buildPacket()); } catch(Exception e){} }

    // 3. RF Gain
    public void setRfGain(int level) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setRfGain(level).buildPacket()); } catch(Exception e){} }

    // 4. AGC
    public void setAgc(int mode) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setAgc(mode).buildPacket()); } catch(Exception e){} }

    // 5. Noise Blanker
    public void setNoiseBlanker(int mode) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setNoiseBlanker(mode).buildPacket()); } catch(Exception e){} }

    // 6. IF Shift / HPF / LPF
    public void setIfShift(int hz) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setIfShift(hz).buildPacket()); } catch(Exception e){} }
    public void setHpf(int code) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setHpf(code).buildPacket()); } catch(Exception e){} }
    public void setLpf(int code) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setLpf(code).buildPacket()); } catch(Exception e){} }

    // 7. Search limits
    public void setSearchLimits(long low, long high) {
        if(transport!=null && transport.isConnected()) try{
            transport.write(CommandFactory.setSearchLower(low).buildPacket());
            transport.write(CommandFactory.setSearchUpper(high).buildPacket());
        } catch(Exception e){}
    }

    // 8. CTCSS/DCS
    public void setCtcss(int code) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setCtcss(code).buildPacket()); } catch(Exception e){} }
    public void setDcs(int code) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setDcs(code).buildPacket()); } catch(Exception e){} }

    // 10. LCD Text
    public void setLcdText(String txt) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setLcdText(txt).buildPacket()); } catch(Exception e){} }

    // 11. Offset
    public void setOffset(long hz) { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.setOffset(hz).buildPacket()); } catch(Exception e){} }
    public void clearOffset() { if(transport!=null && transport.isConnected()) try{ transport.write(CommandFactory.clearOffset().buildPacket()); } catch(Exception e){} }
}