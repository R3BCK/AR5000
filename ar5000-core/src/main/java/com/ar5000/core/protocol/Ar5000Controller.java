// Ar5000Controller.java
package com.ar5000.core.protocol;

import com.ar5000.core.model.ReceiverState;
import com.ar5000.core.transport.Transport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class Ar5000Controller {
    private final Transport transport;
    private final ExecutorService ioExecutor;
    private final ResponseParser parser;
    private final AtomicBoolean listening = new AtomicBoolean(false);

    // ===== STATE TRACKING =====
    private final ReceiverState currentState = new ReceiverState();
    public ReceiverState getCurrentState() { return currentState; }

    public Ar5000Controller(Transport transport) {
        this.transport = transport;
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.parser = new ResponseParser();
    }

    // ===== VFO COMMANDS =====
    public void setFrequency(String vfoId, long freqHz) {
        send(CommandFactory.setFrequency(vfoId, freqHz));
    }

    public void setMode(int modeCode) {
        send(CommandFactory.setMode(modeCode));
    }

    public void setBandwidth(int bwCode) {
        send(CommandFactory.setBandwidth(bwCode));
    }

    // FIX: AR5000 VFO select command: Vx [CR] where x=A/B/C/D/E
    public void selectVfo(String vfoId) {
        send(CommandFactory.selectVfo(vfoId));
    }

    // ===== SQUELCH AND GAIN =====
    public void setSquelch(int level) {
        send(CommandFactory.setSquelch(level));
    }

    // [FIXED] CommandFactory.setRfGain() removed - command "RG" not in spec
    // public void setRfGain(int gain) {
    //     send(CommandFactory.setRfGain(gain));
    // }

    // [FIXED] CommandFactory.setIfShift() removed - command "IS" not in spec
    // public void setIfShift(int shiftHz) {
    //     send(CommandFactory.setIfShift(shiftHz));
    // }

    // ===== TUNING AND STEP =====
    public void setStep(long stepHz) {
        send(CommandFactory.setStep(stepHz));
    }

    // [ADDED] Toggle Step Adjust mode (ST+ command)
    public void toggleStepAdjust() {
        send(CommandFactory.toggleStepAdjust());
    }

    // [FIXED] CommandFactory.setOffset()/clearOffset() removed - command "OF" not in spec
    // public void setOffset(long offsetHz) {
    //     send(CommandFactory.setOffset(offsetHz));
    // }
    // public void clearOffset() {
    //     send(CommandFactory.clearOffset());
    // }

    // ===== ATTENUATOR AND FILTERS =====
    public void setAttenuator(int attCode) {
        send(CommandFactory.setAttenuator(attCode));
    }

    // [FIXED] CommandFactory.setAgc() removed - command "AG" not in spec
    // Use getAgcLevel()/setAgcLevelSend() for AGC level monitoring instead
    // public void setAgc(int agcCode) {
    //     send(CommandFactory.setAgc(agcCode));
    // }

    // [FIXED] CommandFactory.setNoiseBlanker() removed - command "NB" not in spec
    // public void setNoiseBlanker(int nbCode) {
    //     send(CommandFactory.setNoiseBlanker(nbCode));
    // }

    public void setHpf(int hpfCode) {
        send(CommandFactory.setHpf(hpfCode));
    }

    public void setLpf(int lpfCode) {
        send(CommandFactory.setLpf(lpfCode));
    }

    // ===== CTCSS / DCS =====
    public void setCtcss(int ctcssCode) {
        send(CommandFactory.setCtcss(ctcssCode));
    }

    // [FIXED] CommandFactory.setDcs() removed - command "DC" not in spec
    // public void setDcs(int dcsCode) {
    //     send(CommandFactory.setDcs(dcsCode));
    // }

    // ===== SEARCH AND SCAN =====
    public void startScan(int bank) {
        send(CommandFactory.startScan(bank));
    }

    public void startSearch(int bank) {
        send(CommandFactory.startSearch(bank));
    }

    public void setSearchLimits(long low, long high) {
        send(CommandFactory.setSearchLower(low));
        send(CommandFactory.setSearchUpper(high));
    }

    // [ADDED] Read search bank settings (SR command)
    public void getSearchSetting(int bank) {
        send(CommandFactory.getSearchSetting(bank));
    }

    // [ADDED] Read pass frequency list (PR command)
    public void getPassFreqList(int bank, int ch) {
        send(CommandFactory.getPassFreqList(bank, ch));
    }

    // [ADDED] Read select-scan channel list (GR command)
    public void getSelectScanList(int group) {
        send(CommandFactory.getSelectScanList(group));
    }

    // ===== MEMORY OPERATIONS =====
    public void writeMemory(int bank, int ch, long freq, int mode, int bw) {
        send(CommandFactory.writeMemory(bank, ch, freq, mode, bw));
    }

    public void readMemory(int bank, int ch) {
        send(CommandFactory.readMemory(bank, ch));
    }

    public void clearMemory(int bank, int ch) {
        send(CommandFactory.clearMemory(bank, ch));
    }

    // [ADDED] Delete memory channel using MQ command
    public void deleteMemoryChannel(int bank, int ch) {
        send(CommandFactory.deleteMemoryChannel(bank, ch));
    }

    // [ADDED] Set text memo for memory channel (TM command)
    public void setTextMemo(int channel, String text) {
        send(CommandFactory.setTextMemo(channel, text));
    }

    // [ADDED] Get text memo for memory channel (TM command)
    public void getTextMemo(int channel) {
        send(CommandFactory.getTextMemo(channel));
    }

    // ===== CONFIG MENU COMMANDS =====
    // [FIXED] CommandFactory.setLamp() removed - "LM" is for AGC level, not lamp
    // public void setLamp(boolean on) {
    //     send(CommandFactory.setLamp(on));
    // }

    public void setBeep(int volume) {
        send(CommandFactory.setBeep(volume));
    }

    public void setExtIf(int mode) {
        send(CommandFactory.setExtIf(mode));
    }

    // [FIXED] CommandFactory.setBaud() removed - "BS" is for Search-Link Bank
    // public void setBaud(int rate) {
    //     send(CommandFactory.setBaud(rate));
    // }

    public void setAntenna(String mode) {
        send(CommandFactory.setAntenna(mode));
    }

    public void setAntenna(int ant) {
        send(CommandFactory.setAntenna(ant));
    }

    // [FIXED] CommandFactory.setStdInt() removed - command "SI" not in spec
    // public void setStdInt(String mode) {
    //     send(CommandFactory.setStdInt(mode));
    // }

    // ===== OPTION MENU COMMANDS =====
    public void setDtmf(boolean on) {
        send(CommandFactory.setDtmf(on));
    }

    public void setToneElim(int value) {
        send(CommandFactory.setToneElim(value));
    }

    // [FIXED] CommandFactory.setNoiseBlanker() removed - command "NB" not in spec
    // public void setNoiseBlankerLegacy(boolean on) {
    //     send(CommandFactory.setNoiseBlanker(on ? 1 : 0));
    // }

    // ===== DISPLAY AND TEXT =====
    // [FIXED] CommandFactory.setLcdText() removed - "TX" not for LCD text in spec
    // public void setLcdText(String text) {
    //     send(CommandFactory.setLcdText(text));
    // }

    // ===== STATUS SYNC =====
    public void syncState() {
        send(CommandFactory.getStatus());
    }

    // [ADDED] Query AGC level (LM command)
    public void getAgcLevel(boolean squelchOpen) {
        send(CommandFactory.getAgcLevel(squelchOpen));
    }

    // [ADDED] Enable/disable auto-send of AGC level (LC command)
    public void setAgcLevelSend(boolean enabled) {
        send(CommandFactory.setAgcLevelSend(enabled));
    }

    // ===== UTILITY COMMANDS =====
    public void tuneUp() { send(CommandFactory.tuneUp()); }
    public void tuneDown() { send(CommandFactory.tuneDown()); }
    public void exitRemote() { send(CommandFactory.exitRemote()); }
    public void powerOff() { send(CommandFactory.powerOff()); }
    public void powerOn() { send(CommandFactory.powerOn()); }
    public void getVersion() { send(CommandFactory.getVersion()); }
    public void getCwPitch() { send(CommandFactory.getCwPitch()); }
    public void setCwPitch(int code) { send(CommandFactory.setCwPitch(code)); }
    public void setCyberScan(boolean enabled) { send(CommandFactory.setCyberScan(enabled)); }
    public void setAutoMode(boolean enabled) { send(CommandFactory.setAutoMode(enabled)); }
    public void setSubStep(int code) { send(CommandFactory.setSubStep(code)); }
    public void setManualTune(boolean manual) { send(CommandFactory.setManualTune(manual)); }
    public void setTuneSelect(int value) { send(CommandFactory.setTuneSelect(value)); }
    public void setLevelSq(int level) { send(CommandFactory.setLevelSq(level)); }
    public void setVoiceLevel(int level) { send(CommandFactory.setVoiceLevel(level)); }
    public void setDelayTime(float seconds) { send(CommandFactory.setDelayTime(seconds)); }
    public void setSearchBank(int bank) { send(CommandFactory.setSearchBank(bank)); }
    public void setSearchText(String text) { send(CommandFactory.setSearchText(text)); }
    public void deleteSearchWithPass() { send(CommandFactory.deleteSearchWithPass()); }
    public void setSearchLinkGroup(int group) { send(CommandFactory.setSearchLinkGroup(group)); }
    public void setSearchLink(boolean enabled) { send(CommandFactory.setSearchLink(enabled)); }
    public void setSearchLinkBank(int bank) { send(CommandFactory.setSearchLinkBank(bank)); }
    public void setAutoStore(int mode) { send(CommandFactory.setAutoStore(mode)); }
    public void setLevelScan(int level) { send(CommandFactory.setLevelScan(level)); }
    public void setVoiceScan(int level) { send(CommandFactory.setVoiceScan(level)); }
    public void setSearchDelay(float seconds) { send(CommandFactory.setSearchDelay(seconds)); }
    public void setSearchPause(int seconds) { send(CommandFactory.setSearchPause(seconds)); }
    public void startVfoSearch(String vfoId) { send(CommandFactory.startVfoSearch(vfoId)); }
    public void setPassFreq(long freqHz) { send(CommandFactory.setPassFreq(freqHz)); }
    public void setPassFreqCurrent() { send(CommandFactory.setPassFreqCurrent()); }
    public void deletePassFreq(int bank, int ch) { send(CommandFactory.deletePassFreq(bank, ch)); }
    public void setMemoryPass(int ch, boolean enabled) { send(CommandFactory.setMemoryPass(ch, enabled)); }
    public void setMemorySelect(int ch, boolean enabled) { send(CommandFactory.setMemorySelect(ch, enabled)); }
    public void setMemoryLinkGroup(int group) { send(CommandFactory.setMemoryLinkGroup(group)); }
    public void setMemoryLink(boolean enabled) { send(CommandFactory.setMemoryLink(enabled)); }
    public void setMemoryScanLinkBank(int bank) { send(CommandFactory.setMemoryScanLinkBank(bank)); }
    public void setModeScan(int mode) { send(CommandFactory.setModeScan(mode)); }
    public void setMemoryLevelScan(int level) { send(CommandFactory.setMemoryLevelScan(level)); }
    public void setMemoryVoiceScan(int level) { send(CommandFactory.setMemoryVoiceScan(level)); }
    public void setMemoryScanDelay(float seconds) { send(CommandFactory.setMemoryScanDelay(seconds)); }
    public void setMemoryScanPause(int seconds) { send(CommandFactory.setMemoryScanPause(seconds)); }
    public void startSelScan() { send(CommandFactory.startSelScan()); }
    public void setMemoryChannelMode(int bank, int ch) { send(CommandFactory.setMemoryChannelMode(bank, ch)); }
    public void setAfc(boolean enabled) { send(CommandFactory.setAfc(enabled)); }
    public void getPromData() { send(CommandFactory.getPromData()); }
    public void setDeEmphasis(int mode) { send(CommandFactory.setDeEmphasis(mode)); }

    // ===== INTERNAL SEND =====
    public void send(Ar5000Command cmd) {
        if (transport == null) {
            if (parser.getListener() != null) {
                parser.getListener().onError("Cannot send: transport is null");
            }
            return;
        }
        if (!transport.isConnected()) {
            if (parser.getListener() != null) {
                parser.getListener().onError("Cannot send: transport not connected");
            }
            return;
        }
        if (cmd == null) {
            if (parser.getListener() != null) {
                parser.getListener().onError("Cannot send: null command");
            }
            return;
        }
        try {
            transport.write(cmd.buildPacket());
        } catch (IOException e) {
            if (parser.getListener() != null) {
                parser.getListener().onError("TX failed: " + e.getMessage());
            }
        }
    }

    // ===== LISTENING =====
    public void startListening() {
        if (listening.getAndSet(true)) return;
        transport.setListener(new Transport.TransportListener() {
            @Override public void onConnected() {}
            @Override public void onDisconnected() {}

            @Override
            public void onDataReceived(byte[] data) {
                try {
                    String raw = new String(data, StandardCharsets.US_ASCII).trim();
                    if (!raw.isEmpty()) {
                        // Check if this is a status dump (contains multiple tokens like RF/MD/ST)
                        if (raw.contains(" ") && (raw.contains("RF") || raw.contains("MD") || raw.contains("ST"))) {
                            // Parse full status dump and update state
                            parser.parseStatusDump(raw, currentState);
                            // Notify listener about state change (thread-safe copy)
                            if (parser.getListener() != null) {
                                parser.getListener().onStateChanged(currentState.copy());
                            }
                        }
                        // Always parse for single-line responses (backward compatibility)
                        parser.parse(raw);
                    }
                } catch (Exception e) {
                    if (parser.getListener() != null) {
                        parser.getListener().onError("Parse: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                if (parser.getListener() != null) {
                    parser.getListener().onError("Transport: " + e.getMessage());
                }
            }
        });
    }

    public void setResponseListener(ResponseListener l) { parser.setListener(l); }
    public ResponseListener getResponseListener() { return parser.getListener(); }

    // ===== RESPONSE LISTENER INTERFACE =====
    public interface ResponseListener {
        void onFrequencyChanged(String vfo, long freqHz);
        void onModeChanged(int modeCode);
        void onBandwidthChanged(int bwCode);
        void onSignalStrength(int sValue);
        void onBusy(boolean busy);
        void onError(String message);
        void onRawStatus(String raw);
        void onStateChanged(ReceiverState state);
    }

    // ===== LIFECYCLE =====
    public boolean isListening() { return listening.get(); }

    public void shutdown() {
        listening.set(false);
        ioExecutor.shutdownNow();
        parser.setListener(null);
        transport.setListener(null);
    }

    public void restartListening() {
        shutdown();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        startListening();
    }

    public void sendSignalMeterQuery(String vfoId) {
        send(CommandFactory.signalMeterQuery(vfoId));
    }
}