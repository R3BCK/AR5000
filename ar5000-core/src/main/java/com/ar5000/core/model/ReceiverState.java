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
 * Методы: getMemoryChannel(), setMemoryChannel(), isMemoryLoaded(), resetMemoryBank()
 */
public class ReceiverState {

    // ===== CORE STATE (from RX dump) =====
    private String vfo = "A";                    // VFO: A/B/C/D/E
    private long frequencyHz = 145_000_000L;     // RF: частота в Гц
    private long stepHz = 5000L;                 // ST: шаг в Гц
    private boolean autoMode = false;            // AU: 0=OFF, 1=ON
    private int modeCode = Ar5000Protocol.MODE_FM; // MD: 0-4
    private int bwCode = Ar5000Protocol.BW_AUTO; // BW: полоса пропускания (0-6 или -1=AUTO)
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
    // [ADDED] Массив памяти: банки 0-9, каналы 0-99
    // Каждый элемент может быть null (канал не загружен) или содержать MemoryChannel
    private final MemoryChannel[][] memoryBanks = new MemoryChannel[10][100];
    // [ADDED] Флаги: какие банки уже загружены с устройства
    private final boolean[] memoryLoaded = new boolean[10];

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

    // ===== MEMORY OPERATIONS =====

    /**
     * Проверяет, загружен ли банк памяти с устройства.
     * @param bank индекс банка 0-9
     * @return true если банк загружен
     */
    public boolean isMemoryLoaded(int bank) {
        if (bank < 0 || bank > 9) return false;
        return memoryLoaded[bank];
    }

    /**
     * Получает данные канала из памяти.
     * @param bank индекс банка 0-9
     * @param channel индекс канала 0-99
     * @return MemoryChannel или null если канал пуст/не загружен
     */
    public MemoryChannel getMemoryChannel(int bank, int channel) {
        if (bank < 0 || bank > 9 || channel < 0 || channel > 99) return null;
        return memoryBanks[bank][channel];
    }

    /**
     * Обновляет данные канала в памяти (локально).
     * Для сохранения на устройстве используйте CommandFactory.writeMemory() + отправку.
     * @param bank индекс банка 0-9
     * @param channel индекс канала 0-99
     * @param data новые данные канала или null для очистки
     */
    public void setMemoryChannel(int bank, int channel, MemoryChannel data) {
        if (bank < 0 || bank > 9 || channel < 0 || channel > 99) return;
        memoryBanks[bank][channel] = data;
        if (data != null) memoryLoaded[bank] = true;
    }

    /**
     * Очищает все каналы в указанном банке (локально).
     * Для удаления на устройстве используйте CommandFactory.clearMemory() или deleteMemoryChannel().
     * @param bank индекс банка 0-9
     */
    public void resetMemoryBank(int bank) {
        if (bank < 0 || bank > 9) return;
        Arrays.fill(memoryBanks[bank], null);
        memoryLoaded[bank] = false;
    }

    /**
     * Помечает банк как загруженный (вызывается после успешного чтения всех каналов).
     * @param bank индекс банка 0-9
     */
    public void markMemoryLoaded(int bank) {
        if (bank >= 0 && bank <= 9) memoryLoaded[bank] = true;
    }

    /**
     * Возвращает количество загруженных каналов в банке.
     * @param bank индекс банка 0-9
     * @return число от 0 до 100
     */
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
        // Memory: shallow copy of arrays (MemoryChannel is immutable)
        for (int b = 0; b < 10; b++) {
            c.memoryLoaded[b] = this.memoryLoaded[b];
            for (int ch = 0; ch < 100; ch++) {
                c.memoryBanks[b][ch] = this.memoryBanks[b][ch];
            }
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

        // Factory для пустого канала
        public static MemoryChannel empty(int index) {
            return new MemoryChannel(index, 0, 0, 0, "");
        }

        // Factory для канала с данными (из команды MA)
        public static MemoryChannel fromData(int index, long freq, int mode, int bw, String memo) {
            return new MemoryChannel(index, freq, mode, bw, memo);
        }

        // String converters
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

        // Builder для обновления (возвращает новый экземпляр)
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