// CommandFactory.java
package com.ar5000.core.protocol;

public class CommandFactory {

    // ===== CORE VFO/FREQUENCY COMMANDS =====
    public static Ar5000Command setFrequency(String vfoId, long freq) {
        // VA145000000 (VFO letter + frequency in Hz, NO space)
        String header = "V" + vfoId.toUpperCase();
        return new Ar5000Command(header, true, true).addParam(String.valueOf(freq));
    }

    public static Ar5000Command getFrequency(String vfoId) {
        return new Ar5000Command("RF", false, true);
    }

    public static Ar5000Command selectVfo(String vfoId) {
        // Vx where x=A/B/C/D/E (no parameters, no spaces)
        return new Ar5000Command("V" + vfoId.toUpperCase(), true, true);
    }

    public static Ar5000Command getStatus() {
        // RX command requests full status dump
        return new Ar5000Command("RX", false, true);
    }

    // ===== GAIN / SHIFT / OFFSET COMMANDS =====
    public static Ar5000Command setRfGain(int level) {
        // RGnnn: 0-255
        return new Ar5000Command("RG", true, true).addParam(String.valueOf(Math.max(0, Math.min(255, level))));
    }

    public static Ar5000Command setIfShift(int shiftHz) {
        // ISnnnn: -3000 to +3000 Hz
        return new Ar5000Command("IS", true, true).addParam(String.valueOf(Math.max(-3000, Math.min(3000, shiftHz))));
    }

    public static Ar5000Command setOffset(long offsetHz) {
        // OFnnnnnn: offset in Hz
        return new Ar5000Command("OF", true, true).addParam(String.valueOf(offsetHz));
    }

    public static Ar5000Command clearOffset() {
        return new Ar5000Command("OF", true, true).addParam("0");
    }

    // ===== FILTERS & AGC COMMANDS =====
    public static Ar5000Command setAgc(int agcCode) {
        // AGn: n=0(OFF),1(SLOW),2(MID),3(FAST)
        return new Ar5000Command("AG", true, true).addParam(String.valueOf(Math.max(0, Math.min(3, agcCode))));
    }

    public static Ar5000Command setNoiseBlanker(int nbCode) {
        // NBn: n=0(OFF),1(NB1),2(NB2)
        return new Ar5000Command("NB", true, true).addParam(String.valueOf(Math.max(0, Math.min(2, nbCode))));
    }

    public static Ar5000Command setHpf(int hpfCode) {
        // HPn: n=0(0.05K),1(0.2K),2(0.3K),3(0.4K)
        return new Ar5000Command("HP", true, true).addParam(String.valueOf(Math.max(0, Math.min(3, hpfCode))));
    }

    public static Ar5000Command setLpf(int lpfCode) {
        // LPn: n=0(3K),1(4K),2(6K),3(12K)
        return new Ar5000Command("LP", true, true).addParam(String.valueOf(Math.max(0, Math.min(3, lpfCode))));
    }

    public static Ar5000Command setAttenuator(int attCode) {
        // ATn: n=0(0dB),1(10dB),2(20dB),F(AUTO)
        if (attCode == 3) {
            return new Ar5000Command("AT", true, true).addParam("F");
        }
        return new Ar5000Command("AT", true, true).addParam(String.valueOf(Math.max(0, Math.min(2, attCode))));
    }

    // ===== TONES COMMANDS =====
    public static Ar5000Command setCtcss(int ctcssCode) {
        // CNnn: nn=00-37
        return new Ar5000Command("CN", true, true).addParam(String.format("%02d", Math.max(0, Math.min(37, ctcssCode))));
    }

    public static Ar5000Command setDcs(int dcsCode) {
        // DCnnn: nnn=0-104
        return new Ar5000Command("DC", true, true).addParam(String.format("%03d", Math.max(0, Math.min(104, dcsCode))));
    }

    // ===== MODE / BANDWIDTH / STEP COMMANDS =====
    public static Ar5000Command setMode(int mode) {
        // MDn: n=0(FM),1(AM),2(LSB),3(USB),4(CW)
        return new Ar5000Command("MD", true, true).addParam(String.valueOf(Math.max(0, Math.min(4, mode))));
    }

    public static Ar5000Command setBandwidth(int bw) {
        // BWn: n=0(0.5K),1(3K),2(6K),3(15K),4(40K),5(110K),6(220K)
        return new Ar5000Command("BW", true, true).addParam(String.valueOf(Math.max(0, Math.min(6, bw))));
    }

    public static Ar5000Command setStep(long stepHz) {
        // STnnnnnn: step in Hz
        return new Ar5000Command("ST", true, true).addParam(String.valueOf(stepHz));
    }

    // ===== SQUELCH / BEEP / ANTENNA COMMANDS =====
    public static Ar5000Command setSquelch(int level) {
        // RQnnn: 0-255
        return new Ar5000Command("RQ", true, true).addParam(String.valueOf(Math.max(0, Math.min(255, level))));
    }

    public static Ar5000Command setBeep(int volume) {
        // VLnnn: 0-255 (Beep Volume)
        return new Ar5000Command("VL", true, true).addParam(String.valueOf(Math.max(0, Math.min(255, volume))));
    }

    public static Ar5000Command setAntenna(int ant) {
        // ANn: n=0-4 (0=AUTO)
        return new Ar5000Command("AN", true, true).addParam(String.valueOf(Math.max(0, Math.min(4, ant))));
    }

    // Backward compatibility alias
    public static Ar5000Command setAntenna(String mode) {
        String ant = mode.toUpperCase().trim();
        if (ant.equals("AUTO") || ant.equals("ANT 0")) return setAntenna(0);
        if (ant.equals("ANT 1")) return setAntenna(1);
        if (ant.equals("ANT 2")) return setAntenna(2);
        if (ant.equals("ANT 3")) return setAntenna(3);
        if (ant.equals("ANT 4")) return setAntenna(4);
        return setAntenna(1); // Default
    }

    // ===== CONFIG / SYSTEM COMMANDS =====
    public static Ar5000Command setLamp(boolean on) {
        return new Ar5000Command("LM", true, true).addParam(on ? "ON" : "OFF");
    }

    public static Ar5000Command setExtIf(int mode) {
        // AIn: n=0(OFF),1(IF1 ON),2(IF2 ON)
        return new Ar5000Command("AI", true, true).addParam(String.valueOf(Math.max(0, Math.min(2, mode))));
    }

    public static Ar5000Command setBaud(int rate) {
        return new Ar5000Command("BS", true, true).addParam(String.valueOf(rate));
    }

    public static Ar5000Command setStdInt(String mode) {
        // SIn: Standard Interval setting
        return new Ar5000Command("SI", true, true).addParam(mode.toUpperCase());
    }

    public static Ar5000Command setLcdText(String text) {
        String safe = text != null ? text.substring(0, Math.min(16, text.length())).replaceAll("[^\\x20-\\x7E]", " ") : "";
        return new Ar5000Command("TX", true, true).addParam(safe);
    }

    // ===== MEMORY COMMANDS =====
    public static Ar5000Command writeMemory(int bank, int ch, long freq, int mode, int bw) {
        // MXnmm RFfreq MDmode BWbw
        String params = String.format("%1d%02d RF%d MD%d BW%d", bank, ch, freq, mode, bw);
        return new Ar5000Command("MX", true, true).addParam(params);
    }

    public static Ar5000Command readMemory(int bank, int ch) {
        // MAnmm: Read specific bank/channel
        return new Ar5000Command("MA", false, true).addParam(String.format("%1d%02d", bank, ch));
    }

    public static Ar5000Command clearMemory(int bank, int ch) {
        // MCnmm or MQnnmm: Clear specific bank/channel
        return new Ar5000Command("MC", true, true).addParam(String.format("%1d%02d", bank, ch));
    }

    // ===== SCAN & SEARCH COMMANDS =====
    public static Ar5000Command startScan(int bank) {
        // MSn: Start Memory Scan (n=0-9)
        return new Ar5000Command("MS", true, true).addParam(String.valueOf(Math.max(0, Math.min(9, bank))));
    }

    public static Ar5000Command startSearch(int bank) {
        // SSnn: Start Search (nn=00-19)
        return new Ar5000Command("SS", true, true).addParam(String.format("%02d", Math.max(0, Math.min(19, bank))));
    }

    public static Ar5000Command setSearchLimits(long low, long high) {
        // SL... and SU...
        Ar5000Command c1 = new Ar5000Command("SL", true, true).addParam(String.valueOf(low));
        Ar5000Command c2 = new Ar5000Command("SU", true, true).addParam(String.valueOf(high));
        // Note: In practice, send sequentially. This returns SU for chaining.
        return c2;
    }

    public static Ar5000Command setSearchLower(long freqHz) {
        // SLnnnnnnnnnn (Hz)
        return new Ar5000Command("SL", true, true).addParam(String.valueOf(freqHz));
    }

    public static Ar5000Command setSearchUpper(long freqHz) {
        // SUnnnnnnnnnn (Hz)
        return new Ar5000Command("SU", true, true).addParam(String.valueOf(freqHz));
    }

    // ===== UTILITY / STATUS COMMANDS =====
    public static Ar5000Command getVersion() {
        return new Ar5000Command("VR", false, true);
    }

    public static Ar5000Command signalMeterQuery(String vfo) {
        return new Ar5000Command("V" + vfo.toUpperCase() + "SM", false, true);
    }

    // ===== PDF EXTENSIONS (Full AR5000 Command Set) =====
    public static Ar5000Command getAgcLevel(boolean squelchOpen) {
        return new Ar5000Command(squelchOpen ? "LM" : "LM%", false, true);
    }
    public static Ar5000Command setAgcLevelSend(boolean enabled) {
        return new Ar5000Command("LC", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command tuneUp() { return new Ar5000Command("UP", true, true); }
    public static Ar5000Command tuneDown() { return new Ar5000Command("DOWN", true, true); }
    public static Ar5000Command exitRemote() { return new Ar5000Command("EX", true, true); }
    public static Ar5000Command powerOff() { return new Ar5000Command("QP", true, true); }
    public static Ar5000Command powerOn() { return new Ar5000Command("X", true, true); }
    public static Ar5000Command getCwPitch() { return new Ar5000Command("CW", false, true); }
    public static Ar5000Command setCwPitch(int code) {
        return new Ar5000Command("CW", true, true).addParam(String.valueOf(Math.max(0, Math.min(7, code))));
    }
    public static Ar5000Command setCyberScan(boolean enabled) {
        return new Ar5000Command("DS", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setAutoMode(boolean enabled) {
        return new Ar5000Command("AU", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setStepAdjust(long adjustHz) {
        return new Ar5000Command("SH", true, true).addParam(String.valueOf(adjustHz));
    }
    public static Ar5000Command setSubStep(int code) {
        return new Ar5000Command("SJ", true, true).addParam(String.format("%X", Math.max(0, Math.min(10, code))));
    }
    public static Ar5000Command setDtmf(boolean enabled) {
        return new Ar5000Command("QM", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setToneElim(int value) {
        return new Ar5000Command("LS", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, value))));
    }
    public static Ar5000Command setScramble(int code) {
        return new Ar5000Command("SC", true, true).addParam(String.format("%03d", Math.max(0, Math.min(127, code))));
    }
    public static Ar5000Command setManualTune(boolean manual) {
        return new Ar5000Command("MT", true, true).addParam(manual ? "1" : "0");
    }
    public static Ar5000Command setTuneSelect(int value) {
        return new Ar5000Command("TU", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, value))));
    }
    public static Ar5000Command setLevelSq(int level) {
        return new Ar5000Command("DB", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setVoiceLevel(int level) {
        return new Ar5000Command("DA", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setDelayTime(float seconds) {
        int val = Math.round(seconds * 10);
        return new Ar5000Command("DD", true, true).addParam(String.format("%02d", Math.max(0, Math.min(99, val))));
    }
    public static Ar5000Command setSearchBank(int bank) {
        return new Ar5000Command("SE", true, true).addParam(String.format("%02d", Math.max(0, Math.min(19, bank))));
    }
    public static Ar5000Command setSearchText(String text) {
        String safe = text != null ? text.substring(0, Math.min(8, text.length())).replaceAll("[^\\x20-\\x7E]", " ") : "";
        return new Ar5000Command("TT", true, true).addParam(safe);
    }
    public static Ar5000Command deleteSearchWithPass() { return new Ar5000Command("QS", true, true); }
    public static Ar5000Command setSearchLinkGroup(int group) {
        if (group < 0) return new Ar5000Command("GS%%", true, true);
        return new Ar5000Command("GS", true, true).addParam(String.valueOf(Math.max(0, Math.min(9, group))));
    }
    public static Ar5000Command setSearchLink(boolean enabled) {
        return new Ar5000Command("BQ", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setSearchLinkBank(int bank) {
        if (bank < 0) return new Ar5000Command("BS%%", true, true);
        return new Ar5000Command("BS", true, true).addParam(String.format("%02d", Math.max(0, Math.min(19, bank))));
    }
    public static Ar5000Command setAutoStore(int mode) {
        return new Ar5000Command("AS", true, true).addParam(String.valueOf(Math.max(0, Math.min(2, mode))));
    }
    public static Ar5000Command setLevelScan(int level) {
        return new Ar5000Command("SB", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setVoiceScan(int level) {
        return new Ar5000Command("SA", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setSearchDelay(float seconds) {
        if (seconds < 0) return new Ar5000Command("SD", true, true).addParam("FF");
        int val = Math.round(seconds * 10);
        return new Ar5000Command("SD", true, true).addParam(String.format("%02d", Math.max(0, Math.min(99, val))));
    }
    public static Ar5000Command setSearchPause(int seconds) {
        return new Ar5000Command("SP", true, true).addParam(String.format("%02d", Math.max(0, Math.min(60, seconds))));
    }
    public static Ar5000Command startVfoSearch(String vfoId) {
        return new Ar5000Command("VS", true, true).addParam(vfoId.toUpperCase());
    }
    public static Ar5000Command setPassFreq(long freqHz) {
        return new Ar5000Command("PS", true, true).addParam(String.valueOf(freqHz));
    }
    public static Ar5000Command setPassFreqCurrent() { return new Ar5000Command("PW", true, true); }
    public static Ar5000Command deletePassFreq(int bank, int ch) {
        if (bank < 0) return new Ar5000Command("PD%%", true, true).addParam(String.format("%02d", ch));
        return new Ar5000Command("PD", true, true).addParam(String.format("%02d%02d", bank, ch));
    }
    public static Ar5000Command setMemoryPass(int ch, boolean enabled) {
        if (ch < 0) return new Ar5000Command("MP%%", true, true);
        return new Ar5000Command("MP", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setMemorySelect(int ch, boolean enabled) {
        if (ch < 0) return new Ar5000Command("GA%%", true, true);
        return new Ar5000Command("GA", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setMemoryLinkGroup(int group) {
        if (group < 0) return new Ar5000Command("GM%%", true, true);
        return new Ar5000Command("GM", true, true).addParam(String.valueOf(Math.max(0, Math.min(9, group))));
    }
    public static Ar5000Command setMemoryLink(boolean enabled) {
        return new Ar5000Command("ML", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command setMemoryScanLinkBank(int bank) {
        if (bank < 0) return new Ar5000Command("BM%%", true, true);
        return new Ar5000Command("BM", true, true).addParam(String.valueOf(Math.max(0, Math.min(9, bank))));
    }
    public static Ar5000Command setModeScan(int mode) {
        return new Ar5000Command("XM", true, true).addParam(mode == 5 ? "F" : String.valueOf(Math.max(0, Math.min(4, mode))));
    }
    public static Ar5000Command setMemoryLevelScan(int level) {
        return new Ar5000Command("XB", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setMemoryVoiceScan(int level) {
        return new Ar5000Command("XA", true, true).addParam(String.format("%03d", Math.max(0, Math.min(255, level))));
    }
    public static Ar5000Command setMemoryScanDelay(float seconds) {
        int val = Math.round(seconds * 10);
        return new Ar5000Command("XD", true, true).addParam(String.format("%02d", Math.max(0, Math.min(99, val))));
    }
    public static Ar5000Command setMemoryScanPause(int seconds) {
        return new Ar5000Command("XP", true, true).addParam(String.format("%02d", Math.max(0, Math.min(60, seconds))));
    }
    public static Ar5000Command startSelScan() { return new Ar5000Command("SM", true, true); }
    public static Ar5000Command setMemoryChannelMode(int bank, int ch) {
        return new Ar5000Command("MR", true, true).addParam(String.format("%1d%02d", bank, ch));
    }
    public static Ar5000Command setAfc(boolean enabled) {
        return new Ar5000Command("AF", true, true).addParam(enabled ? "1" : "0");
    }
    public static Ar5000Command getPromData() { return new Ar5000Command("DM", false, true); }
    public static Ar5000Command setDeEmphasis(int mode) {
        return new Ar5000Command("EN", true, true).addParam(String.valueOf(mode));
    }
}