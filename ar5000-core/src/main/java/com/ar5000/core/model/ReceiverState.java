// ReceiverState.java
package com.ar5000.core.model;

import com.ar5000.core.protocol.Ar5000Protocol;

public class ReceiverState {
    // ===== EXISTING FIELDS =====
    private long frequencyHz = 145_000_000;
    private int modeCode = Ar5000Protocol.MODE_FM;
    private int bwCode = Ar5000Protocol.BW_15K;
    private int signalStrength = 0;
    private boolean isRemoteMode = false;
    private boolean isBusy = false;
    private int bank = 0;
    private int channel = 0;

    // ===== NEW FIELDS FOR ADVANCED FEATURES =====
    private long stepHz = 5000;              // Step tuning
    private int attenuator = 0;              // Att: 0=OFF,1=10dB,2=20dB,3=AUTO
    private int rfGain = 128;                // RF Gain: 0-255
    private int agcMode = Ar5000Protocol.MODE_AUTO; // AGC: 0=OFF,1=SLOW,2=MID,3=FAST
    private int noiseBlanker = 0;            // NB: 0=OFF,1=NB1,2=NB2
    private int ifShift = 0;                 // IF Shift: -3000..+3000 Hz
    private int hpfCode = 0;                 // HPF: 0-3
    private int lpfCode = 0;                 // LPF: 0-3
    private long searchLower = 0;            // Search lower limit
    private long searchUpper = 0;            // Search upper limit
    private int ctcssCode = 0;               // CTCSS code
    private int dcsCode = 0;                 // DCS code
    private String lcdText = "";             // LCD text (max 16 chars)
    private long offsetHz = 0;               // Offset tuning
    private int agcLevel = 0;                // AGC level: 0-255
    private boolean squelchOpen = false;     // Squelch open flag
    private String vfo = "A";                // Current VFO: A/B/C/D/E
    private int antenna = 0;                 // Antenna: 0=AUTO,1,2,3,4
    private boolean autoMode = false;        // Auto mode flag

    // ===== GETTERS/SETTERS FOR NEW FIELDS =====
    public long getStepHz() { return stepHz; }
    public void setStepHz(long hz) { this.stepHz = hz; }

    public int getAttenuator() { return attenuator; }
    public void setAttenuator(int att) { this.attenuator = att; }

    public int getRfGain() { return rfGain; }
    public void setRfGain(int gain) { this.rfGain = gain; }

    public int getAgcMode() { return agcMode; }
    public void setAgcMode(int agc) { this.agcMode = agc; }

    public int getNoiseBlanker() { return noiseBlanker; }
    public void setNoiseBlanker(int nb) { this.noiseBlanker = nb; }

    public int getIfShift() { return ifShift; }
    public void setIfShift(int shift) { this.ifShift = shift; }

    public int getHpfCode() { return hpfCode; }
    public void setHpfCode(int code) { this.hpfCode = code; }

    public int getLpfCode() { return lpfCode; }
    public void setLpfCode(int code) { this.lpfCode = code; }

    public long getSearchLower() { return searchLower; }
    public void setSearchLower(long f) { this.searchLower = f; }

    public long getSearchUpper() { return searchUpper; }
    public void setSearchUpper(long f) { this.searchUpper = f; }

    public int getCtcssCode() { return ctcssCode; }
    public void setCtcssCode(int code) { this.ctcssCode = code; }

    public int getDcsCode() { return dcsCode; }
    public void setDcsCode(int code) { this.dcsCode = code; }

    public String getLcdText() { return lcdText; }
    public void setLcdText(String txt) {
        this.lcdText = txt != null ? txt.substring(0, Math.min(16, txt.length())) : "";
    }

    public long getOffsetHz() { return offsetHz; }
    public void setOffsetHz(long off) { this.offsetHz = off; }

    public int getAgcLevel() { return agcLevel; }
    public void setAgcLevel(int level) { this.agcLevel = level; }

    public boolean isSquelchOpen() { return squelchOpen; }
    public void setSquelchOpen(boolean open) { this.squelchOpen = open; }

    public String getVfo() { return vfo; }
    public void setVfo(String v) { this.vfo = v != null ? v.toUpperCase() : "A"; }

    public int getAntenna() { return antenna; }
    public void setAntenna(int ant) { this.antenna = Math.max(0, Math.min(4, ant)); }

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean mode) { this.autoMode = mode; }

    // ===== EXISTING GETTERS/SETTERS =====
    public long getFrequencyHz() { return frequencyHz; }
    public void setFrequencyHz(long hz) { this.frequencyHz = hz; }

    public int getModeCode() { return modeCode; }
    public void setModeCode(int code) { this.modeCode = code; }

    public int getBwCode() { return bwCode; }
    public void setBwCode(int code) { this.bwCode = code; }

    public int getSignalStrength() { return signalStrength; }
    public void setSignalStrength(int sig) { this.signalStrength = sig; }

    public boolean isRemoteMode() { return isRemoteMode; }
    public void setRemoteMode(boolean remote) { isRemoteMode = remote; }

    public boolean isBusy() { return isBusy; }
    public void setBusy(boolean busy) { isBusy = busy; }

    public int getBank() { return bank; }
    public void setBank(int bank) { this.bank = bank; }

    public int getChannel() { return channel; }
    public void setChannel(int ch) { this.channel = ch; }

    // ===== STRING CONVERTERS USING Ar5000Protocol =====
    public String getModeString() {
        return Ar5000Protocol.getModeName(modeCode);
    }

    public String getBwString() {
        return Ar5000Protocol.getBwName(bwCode);
    }

    // ===== COPY METHOD FOR THREAD-SAFE UPDATES =====
    public ReceiverState copy() {
        ReceiverState c = new ReceiverState();
        c.frequencyHz = this.frequencyHz; c.modeCode = this.modeCode; c.bwCode = this.bwCode;
        c.signalStrength = this.signalStrength; c.isRemoteMode = this.isRemoteMode;
        c.isBusy = this.isBusy; c.bank = this.bank; c.channel = this.channel;
        c.stepHz = this.stepHz; c.attenuator = this.attenuator; c.rfGain = this.rfGain;
        c.agcMode = this.agcMode; c.noiseBlanker = this.noiseBlanker; c.ifShift = this.ifShift;
        c.hpfCode = this.hpfCode; c.lpfCode = this.lpfCode; c.searchLower = this.searchLower;
        c.searchUpper = this.searchUpper; c.ctcssCode = this.ctcssCode; c.dcsCode = this.dcsCode;
        c.lcdText = this.lcdText; c.offsetHz = this.offsetHz;
        c.agcLevel = this.agcLevel; c.squelchOpen = this.squelchOpen;
        c.vfo = this.vfo; c.antenna = this.antenna; c.autoMode = this.autoMode;
        return c;
    }
}