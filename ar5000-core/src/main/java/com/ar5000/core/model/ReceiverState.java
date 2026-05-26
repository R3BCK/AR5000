// ReceiverState.java
package com.ar5000.core.model;

import com.ar5000.core.protocol.Ar5000Protocol;
import java.util.Arrays;

/**
 * Состояние приёмника, обновляемое через команду RX (полный дамп).
 * Содержит только те поля, которые возвращаются в ответе RX согласно спецификации.
 *
 * Потокобезопасность: используйте copy() для передачи в UI-поток.
 *
 * Встроенная поддержка памяти: 10 банков × 100 каналов.
 * Встроенная поддержка всех 5 VFO (A-E): частота, режим, полоса, шаг.
 * Методы: getMemoryChannel(), setMemoryChannel(), isMemoryLoaded(), resetMemoryBank()
 * Методы для VFO: getVfoState(), getAllVfoStates(), updateVfoFromResponse()
 */
public class ReceiverState {

    // ===== CORE STATE (from RX dump - active VFO only) =====
    private String vfo = "A";                    // VFO: A/B/C/D/E (активный)
    private long frequencyHz = 145_000_000L;     // RF: частота в Гц (активный VFO)
    private long stepHz = 5000L;                 // ST: шаг в Гц (активный VFO)
    private boolean autoMode = false;            // AU: 0=OFF, 1=ON
    private int modeCode = Ar5000Protocol.MODE_FM; // MD: 0-4 (активный VFO)
    private int bwCode = Ar5000Protocol.BW_AUTO; // BW: полоса пропускания (активный VFO)
    private int attenuator = Ar5000Protocol.ATT_0DB; // AT: 0,1,2,3(AUTO)
    private int antenna = Ar5000Protocol.ANT_AUTO;   // AN: 0-4
    private int squelchLevel = 0;                // RQ: уровень 0-255
    private boolean squelchOpen = false;         // RQ+: признак открытого сквоша
    private int agcLevel = 0;                    // LM: уровень 0-255 (16-ричный в протоколе)
    private String memoText = "";                // TM: текст текущего канала (до 8 символов)
    private int scanBank = 0;                    // Текущий банк сканирования (0-9)
    private int signalStrength = 0;              // Вычисляется из AGC-уровня или отдельного ответа

    // ===== EXTENDED STATE (optional, may require separate queries) =====
    private int hpfCode = Ar5000Protocol.HPF_0_05K;
    private int lpfCode = Ar5000Protocol.LPF_3K;
    private int cwPitchCode = 1;
    private int deEmphasisMode = 0;
    private int tuneSelectValue = 0;
    private int levelSqValue = 0;
    private int voiceLevelValue = 100;
    private int delayTimeDeciSec = 5;
    private boolean cyberScanEnabled = false;
    private boolean manualTuneEnabled = false;
    private String firmwareVersion = "";

    // ===== STATUS FLAGS =====
    private boolean isRemoteMode = false;
    private boolean isBusy = false;

    // ===== MEMORY STORAGE: 10 banks × 100 channels =====
    private final MemoryChannel[][] memoryBanks = new MemoryChannel[10][100];
    private final boolean[] memoryLoaded = new boolean[10];

    // ===== [ADDED] VFO ARRAY STATE (for all 5 VFOs A-E) =====
    // Массив состояний для всех 5 VFO: индексы 0='A', 1='B', ..., 4='E'
    private final VfoState[] vfoStates = new VfoState[5];

    // Инициализация массива VFO при создании объекта
    {
        for (int i = 0; i < 5; i++) {
            vfoStates[i] = new VfoState((char) ('A' + i));
        }
    }

    // ===== GETTERS/SETTERS FOR CORE STATE =====

    public String getVfo() { return vfo; }
    public void setVfo(String v) { this.vfo = v != null ? v.toUpperCase() : "A"; }

    public long getFrequencyHz() { return frequencyHz; }
    public void setFrequencyHz(long hz) { this.frequencyHz = hz; }

    public long getStepHz() { return stepHz; }
    public void setStepHz(long hz) { this.stepHz = hz; }

    public boolean isAutoMode() { return autoMode; }
    public void setAutoMode(boolean mode) { this.autoMode = mode; }

    public int getModeCode() { return modeCode; }
    public void setModeCode(int code) {
        if (Ar5000Protocol.isValidMode(code)) this.modeCode = code;
    }

    public int getBwCode() { return bwCode; }
    public void setBwCode(int code) {
        if (Ar5000Protocol.isValidBw(code)) this.bwCode = code;
    }

    public String getBwString() {
        return Ar5000Protocol.getBwName(bwCode);
    }

    public int getAttenuator() { return attenuator; }
    public void setAttenuator(int att) {
        if (Ar5000Protocol.isValidAttenuator(att)) this.attenuator = att;
    }

    public int getAntenna() { return antenna; }
    public void setAntenna(int ant) {
        if (Ar5000Protocol.isValidAntenna(ant)) this.antenna = ant;
    }

    public int getSquelchLevel() { return squelchLevel; }
    public void setSquelchLevel(int level) {
        this.squelchLevel = Math.max(0, Math.min(255, level));
    }

    public boolean isSquelchOpen() { return squelchOpen; }
    public void setSquelchOpen(boolean open) { this.squelchOpen = open; }

    public int getAgcLevel() { return agcLevel; }
    public void setAgcLevel(int level) {
        this.agcLevel = Math.max(0, Math.min(255, level));
    }

    public String getMemoText() { return memoText; }
    public void setMemoText(String txt) {
        this.memoText = txt != null ? txt.substring(0, Math.min(8, txt.length())) : "";
    }

    public int getScanBank() { return scanBank; }
    public void setScanBank(int bank) {
        if (bank >= 0 && bank <= 9) this.scanBank = bank;
    }

    public int getSignalStrength() { return signalStrength; }
    public void setSignalStrength(int sig) {
        this.signalStrength = Math.max(0, Math.min(255, sig));
    }

    // ===== GETTERS/SETTERS FOR EXTENDED STATE =====

    public int getHpfCode() { return hpfCode; }
    public void setHpfCode(int code) {
        if (Ar5000Protocol.isValidHpf(code)) this.hpfCode = code;
    }

    public int getLpfCode() { return lpfCode; }
    public void setLpfCode(int code) {
        if (Ar5000Protocol.isValidLpf(code)) this.lpfCode = code;
    }

    public int getCwPitchCode() { return cwPitchCode; }
    public void setCwPitchCode(int code) {
        if (Ar5000Protocol.isValidCwPitch(code)) this.cwPitchCode = code;
    }

    public int getCwPitchHz() {
        return Ar5000Protocol.getCwPitchFreq(cwPitchCode);
    }

    public int getDeEmphasisMode() { return deEmphasisMode; }
    public void setDeEmphasisMode(int mode) { this.deEmphasisMode = mode; }

    public int getTuneSelectValue() { return tuneSelectValue; }
    public void setTuneSelectValue(int val) { this.tuneSelectValue = Math.max(0, Math.min(255, val)); }

    public int getLevelSqValue() { return levelSqValue; }
    public void setLevelSqValue(int val) { this.levelSqValue = Math.max(0, Math.min(255, val)); }

    public int getVoiceLevelValue() { return voiceLevelValue; }
    public void setVoiceLevelValue(int val) { this.voiceLevelValue = Math.max(0, Math.min(255, val)); }

    public int getDelayTimeDeciSec() { return delayTimeDeciSec; }
    public void setDelayTimeDeciSec(int val) {
        this.delayTimeDeciSec = Math.max(0, Math.min(99, val));
    }

    public float getDelayTimeSec() {
        return delayTimeDeciSec / 10.0f;
    }

    public boolean isCyberScanEnabled() { return cyberScanEnabled; }
    public void setCyberScanEnabled(boolean enabled) { this.cyberScanEnabled = enabled; }

    public boolean isManualTuneEnabled() { return manualTuneEnabled; }
    public void setManualTuneEnabled(boolean enabled) { this.manualTuneEnabled = enabled; }

    public String getFirmwareVersion() { return firmwareVersion; }
    public void setFirmwareVersion(String ver) { this.firmwareVersion = ver != null ? ver : ""; }

    // ===== STATUS FLAGS =====

    public boolean isRemoteMode() { return isRemoteMode; }
    public void setRemoteMode(boolean remote) { isRemoteMode = remote; }

    public boolean isBusy() { return isBusy || squelchOpen; }
    public void setBusy(boolean busy) { isBusy = busy; }

    // ===== STRING CONVERTERS =====

    public String getModeString() {
        return Ar5000Protocol.getModeName(modeCode);
    }

    public String getAttenuatorString() {
        return Ar5000Protocol.getAttenuatorName(attenuator);
    }

    public String getAntennaString() {
        return Ar5000Protocol.getAntennaName(antenna);
    }

    public String getHpfString() {
        return Ar5000Protocol.getHpfName(hpfCode);
    }

    public String getLpfString() {
        return Ar5000Protocol.getLpfName(lpfCode);
    }

    public String getCwPitchString() {
        return Ar5000Protocol.getCwPitchName(cwPitchCode);
    }

    // ===== [ADDED] VFO ARRAY ACCESSORS =====

    /**
     * Получает состояние конкретного VFO (A-E).
     * @param vfoId буква 'A'..'E' или строка "A".."E"
     * @return VfoState или null если неверный идентификатор
     */
    public VfoState getVfoState(Object vfoId) {
        char id;
        if (vfoId instanceof String) {
            String s = (String) vfoId;
            if (s.isEmpty()) return null;
            id = Character.toUpperCase(s.charAt(0));
        } else if (vfoId instanceof Character) {
            id = Character.toUpperCase((Character) vfoId);
        } else {
            return null;
        }
        int idx = id - 'A';
        if (idx >= 0 && idx < 5) return vfoStates[idx];
        return null;
    }

    /**
     * Получает массив состояний всех 5 VFO (копия для потокобезопасности).
     * @return массив из 5 элементов [A,B,C,D,E]
     */
    public VfoState[] getAllVfoStates() {
        VfoState[] copy = new VfoState[5];
        for (int i = 0; i < 5; i++) {
            copy[i] = vfoStates[i].copy();
        }
        return copy;
    }

    /**
     * Обновляет состояние одного VFO из ответа команды (RX дамп или VxRF).
     * Создаёт новый неизменяемый экземпляр VfoState и заменяет ссылку в массиве.
     * @param vfoId буква 'A'..'E'
     * @param freq частота в Гц (или -1 чтобы не менять)
     * @param mode код режима 0-4 (или -1 чтобы не менять)
     * @param bw код полосы 0-6 или -1 (или -1 чтобы не менять)
     * @param step шаг в Гц (или -1 чтобы не менять)
     */
    public void updateVfoFromResponse(String vfoId, long freq, int mode, int bw, long step) {
        int idx = -1;
        if (vfoId != null && !vfoId.isEmpty()) {
            idx = Character.toUpperCase(vfoId.charAt(0)) - 'A';
        }
        if (idx < 0 || idx >= 5) return;

        VfoState current = vfoStates[idx];

        // Используем текущие значения, если новые не переданы (-1)
        long newFreq = (freq >= 0) ? freq : current.frequencyHz;
        int newMode = (mode >= 0 && Ar5000Protocol.isValidMode(mode)) ? mode : current.modeCode;
        int newBw = (bw >= 0 && Ar5000Protocol.isValidBw(bw)) ? bw : current.bwCode;
        long newStep = (step >= 0) ? step : current.stepHz;

        // Создаём новый неизменяемый экземпляр и заменяем ссылку
        vfoStates[idx] = new VfoState(current.id, newFreq, newMode, newBw, newStep);

        // Если это активный VFO — обновляем также "текущее" состояние для обратной совместимости
        if (vfoId != null && vfoId.equalsIgnoreCase(this.vfo)) {
            setFrequencyHz(newFreq);
            setModeCode(newMode);
            setBwCode(newBw);
            setStepHz(newStep);
        }
    }

    // ===== MEMORY OPERATIONS =====

    public boolean isMemoryLoaded(int bank) {
        if (bank < 0 || bank > 9) return false;
        return memoryLoaded[bank];
    }

    public MemoryChannel getMemoryChannel(int bank, int channel) {
        if (bank < 0 || bank > 9 || channel < 0 || channel > 99) return null;
        return memoryBanks[bank][channel];
    }

    public void setMemoryChannel(int bank, int channel, MemoryChannel data) {
        if (bank < 0 || bank > 9 || channel < 0 || channel > 99) return;
        memoryBanks[bank][channel] = data;
        if (data != null) memoryLoaded[bank] = true;
    }

    public void resetMemoryBank(int bank) {
        if (bank < 0 || bank > 9) return;
        Arrays.fill(memoryBanks[bank], null);
        memoryLoaded[bank] = false;
    }

    public void markMemoryLoaded(int bank) {
        if (bank >= 0 && bank <= 9) memoryLoaded[bank] = true;
    }

    public int getLoadedChannelCount(int bank) {
        if (bank < 0 || bank > 9) return 0;
        int count = 0;
        for (MemoryChannel ch : memoryBanks[bank]) {
            if (ch != null && !ch.isEmpty) count++;
        }
        return count;
    }

    // ===== THREAD-SAFE COPY =====

    public ReceiverState copy() {
        ReceiverState c = new ReceiverState();
        // Core state
        c.vfo = this.vfo;
        c.frequencyHz = this.frequencyHz;
        c.stepHz = this.stepHz;
        c.autoMode = this.autoMode;
        c.modeCode = this.modeCode;
        c.bwCode = this.bwCode;
        c.attenuator = this.attenuator;
        c.antenna = this.antenna;
        c.squelchLevel = this.squelchLevel;
        c.squelchOpen = this.squelchOpen;
        c.agcLevel = this.agcLevel;
        c.memoText = this.memoText;
        c.scanBank = this.scanBank;
        c.signalStrength = this.signalStrength;
        // Extended state
        c.hpfCode = this.hpfCode;
        c.lpfCode = this.lpfCode;
        c.cwPitchCode = this.cwPitchCode;
        c.deEmphasisMode = this.deEmphasisMode;
        c.tuneSelectValue = this.tuneSelectValue;
        c.levelSqValue = this.levelSqValue;
        c.voiceLevelValue = this.voiceLevelValue;
        c.delayTimeDeciSec = this.delayTimeDeciSec;
        c.cyberScanEnabled = this.cyberScanEnabled;
        c.manualTuneEnabled = this.manualTuneEnabled;
        c.firmwareVersion = this.firmwareVersion;
        // Flags
        c.isRemoteMode = this.isRemoteMode;
        c.isBusy = this.isBusy;
        // Memory: shallow copy (MemoryChannel is immutable)
        for (int b = 0; b < 10; b++) {
            c.memoryLoaded[b] = this.memoryLoaded[b];
            for (int ch = 0; ch < 100; ch++) {
                c.memoryBanks[b][ch] = this.memoryBanks[b][ch];
            }
        }
        // VFO states: copy references (VfoState is immutable)
        for (int i = 0; i < 5; i++) {
            c.vfoStates[i] = this.vfoStates[i];
        }
        return c;
    }

    // ===== UTILITY =====

    public void reset() {
        vfo = "A";
        frequencyHz = 145_000_000L;
        stepHz = 5000L;
        autoMode = false;
        modeCode = Ar5000Protocol.MODE_FM;
        bwCode = Ar5000Protocol.BW_AUTO;
        attenuator = Ar5000Protocol.ATT_0DB;
        antenna = Ar5000Protocol.ANT_AUTO;
        squelchLevel = 0;
        squelchOpen = false;
        agcLevel = 0;
        memoText = "";
        scanBank = 0;
        signalStrength = 0;
        hpfCode = Ar5000Protocol.HPF_0_05K;
        lpfCode = Ar5000Protocol.LPF_3K;
        cwPitchCode = 1;
        deEmphasisMode = 0;
        tuneSelectValue = 0;
        levelSqValue = 0;
        voiceLevelValue = 100;
        delayTimeDeciSec = 5;
        cyberScanEnabled = false;
        manualTuneEnabled = false;
        firmwareVersion = "";
        isRemoteMode = false;
        isBusy = false;
        // Reset memory
        for (int b = 0; b < 10; b++) {
            Arrays.fill(memoryBanks[b], null);
            memoryLoaded[b] = false;
        }
        // Reset VFO states
        for (int i = 0; i < 5; i++) {
            vfoStates[i] = new VfoState((char) ('A' + i));
        }
    }

    // ===== INNER CLASS: VfoState =====

    /**
     * Состояние одного VFO (A-E).
     * Содержит только те параметры, которые возвращаются командами чтения.
     * Неизменяемый после создания — для обновления создаётся новый экземпляр.
     */
    public static final class VfoState {
        public final char id;              // 'A'..'E'
        public final long frequencyHz;
        public final int modeCode;
        public final int bwCode;
        public final long stepHz;

        public VfoState(char id) {
            this(id, 145_000_000L, Ar5000Protocol.MODE_FM, Ar5000Protocol.BW_AUTO, 5000L);
        }

        public VfoState(char id, long freq, int mode, int bw, long step) {
            if (id < 'A' || id > 'E') {
                throw new IllegalArgumentException("VFO ID must be A-E");
            }
            this.id = id;
            this.frequencyHz = freq;
            this.modeCode = Ar5000Protocol.isValidMode(mode) ? mode : Ar5000Protocol.MODE_FM;
            this.bwCode = Ar5000Protocol.isValidBw(bw) ? bw : Ar5000Protocol.BW_AUTO;
            this.stepHz = step > 0 ? step : 5000L;
        }

        public String getId() { return String.valueOf(id); }

        public String getFrequencyMhzString() {
            long mhz = frequencyHz / 1_000_000;
            long khz = (frequencyHz / 1000) % 1000;
            long hz = frequencyHz % 1000;
            return String.format("%d.%03d.%03d", mhz, khz, hz);
        }

        public String getModeString() { return Ar5000Protocol.getModeName(modeCode); }
        public String getBwString() { return Ar5000Protocol.getBwName(bwCode); }
        public String getStepString() { return stepHz + " Hz"; }

        /**
         * Создаёт копию состояния (для потокобезопасности).
         * Для неизменяемого класса это просто возврат this, но оставляем для совместимости.
         */
        public VfoState copy() {
            return this; // immutable, no need to clone
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof VfoState)) return false;
            VfoState that = (VfoState) o;
            return id == that.id &&
                    frequencyHz == that.frequencyHz &&
                    modeCode == that.modeCode &&
                    bwCode == that.bwCode &&
                    stepHz == that.stepHz;
        }

        @Override
        public int hashCode() {
            int result = id;
            result = 31 * result + (int) (frequencyHz ^ (frequencyHz >>> 32));
            result = 31 * result + modeCode;
            result = 31 * result + bwCode;
            result = 31 * result + (int) (stepHz ^ (stepHz >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "VfoState{" +
                    "id=" + id +
                    ", freq=" + getFrequencyMhzString() +
                    ", mode=" + getModeString() +
                    ", bw=" + getBwString() +
                    ", step=" + stepHz + "Hz" +
                    '}';
        }
    }

    // ===== INNER CLASS: MemoryChannel =====

    /**
     * Представляет один канал памяти (0-99 в банке).
     * Неизменяемый после создания — для обновления создаётся новый экземпляр.
     */
    public static final class MemoryChannel {
        public final int index;           // 0-99
        public final long frequencyHz;
        public final int modeCode;
        public final int bwCode;
        public final String memoText;     // до 8 символов
        public final boolean isEmpty;     // признак пустого канала

        public MemoryChannel(int index, long freq, int mode, int bw, String memo) {
            if (index < 0 || index > 99) {
                throw new IllegalArgumentException("Channel index must be 0-99");
            }
            this.index = index;
            this.frequencyHz = freq;
            this.modeCode = Ar5000Protocol.isValidMode(mode) ? mode : Ar5000Protocol.MODE_FM;
            this.bwCode = Ar5000Protocol.isValidBw(bw) ? bw : Ar5000Protocol.BW_AUTO;
            this.memoText = memo != null ? memo.substring(0, Math.min(8, memo.length())) : "";
            this.isEmpty = (freq == 0 && mode == 0 && bw == 0 && (memo == null || memo.isEmpty()));
        }

        public static MemoryChannel empty(int index) {
            return new MemoryChannel(index, 0, 0, 0, "");
        }

        public static MemoryChannel fromData(int index, long freq, int mode, int bw, String memo) {
            return new MemoryChannel(index, freq, mode, bw, memo);
        }

        public String getFrequencyMhzString() {
            if (isEmpty) return "---.---.---";
            long mhz = frequencyHz / 1_000_000;
            long khz = (frequencyHz / 1000) % 1000;
            long hz = frequencyHz % 1000;
            return String.format("%d.%03d.%03d", mhz, khz, hz);
        }

        public String getModeString() {
            return Ar5000Protocol.getModeName(modeCode);
        }

        public String getBwString() {
            return Ar5000Protocol.getBwName(bwCode);
        }

        public String getMemoText() {
            return memoText != null ? memoText : "";
        }

        public MemoryChannel withFrequency(long freq) {
            return new MemoryChannel(index, freq, modeCode, bwCode, memoText);
        }

        public MemoryChannel withMode(int mode) {
            return new MemoryChannel(index, frequencyHz, mode, bwCode, memoText);
        }

        public MemoryChannel withBandwidth(int bw) {
            return new MemoryChannel(index, frequencyHz, modeCode, bw, memoText);
        }

        public MemoryChannel withMemo(String memo) {
            return new MemoryChannel(index, frequencyHz, modeCode, bwCode, memo);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MemoryChannel)) return false;
            MemoryChannel that = (MemoryChannel) o;
            return index == that.index &&
                    frequencyHz == that.frequencyHz &&
                    modeCode == that.modeCode &&
                    bwCode == that.bwCode &&
                    isEmpty == that.isEmpty &&
                    (memoText != null ? memoText.equals(that.memoText) : that.memoText == null);
        }

        @Override
        public int hashCode() {
            int result = index;
            result = 31 * result + (int) (frequencyHz ^ (frequencyHz >>> 32));
            result = 31 * result + modeCode;
            result = 31 * result + bwCode;
            result = 31 * result + (memoText != null ? memoText.hashCode() : 0);
            result = 31 * result + (isEmpty ? 1 : 0);
            return result;
        }

        @Override
        public String toString() {
            return "MemoryChannel{" +
                    "idx=" + index +
                    ", freq=" + getFrequencyMhzString() +
                    ", mode=" + getModeString() +
                    ", bw=" + getBwString() +
                    ", memo='" + memoText + '\'' +
                    ", empty=" + isEmpty +
                    '}';
        }
    }
}