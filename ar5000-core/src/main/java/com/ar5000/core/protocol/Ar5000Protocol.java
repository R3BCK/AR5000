// Ar5000Protocol.java
package com.ar5000.core.protocol;

import android.util.SparseArray;

public final class Ar5000Protocol {
    private Ar5000Protocol() {}

    // ===== MODE CONSTANTS (per AR-5000 RS232 spec) =====
    // Valid modes: FM=0, AM=1, LSB=2, USB=3, CW=4
    public static final int MODE_AUTO = -1;
    public static final int MODE_FM = 0;
    public static final int MODE_AM = 1;
    public static final int MODE_LSB = 2;
    public static final int MODE_USB = 3;
    public static final int MODE_CW = 4;

    // [REMOVED] MODE_SAM=5, MODE_SAL=6, MODE_SAH=7 not in AR-5000 RS232 spec
    // Using these codes will cause receiver to respond with "?" (unknown command)
    // public static final int MODE_SAM = 5;
    // public static final int MODE_SAL = 6;
    // public static final int MODE_SAH = 7;

    // Mode names array for UI binding (index matches code for 0-4)
    public static final String[] MODE_NAMES = {
            "AUTO", "FM", "AM", "LSB", "USB", "CW"
    };
    public static final int[] MODE_CODES = {-1, 0, 1, 2, 3, 4};

    // SparseArray for O(1) mode name lookup
    private static final SparseArray<String> MODE_NAMES_MAP = new SparseArray<>();
    static {
        MODE_NAMES_MAP.put(MODE_AUTO, "AUTO");
        MODE_NAMES_MAP.put(MODE_FM, "FM");
        MODE_NAMES_MAP.put(MODE_AM, "AM");
        MODE_NAMES_MAP.put(MODE_LSB, "LSB");
        MODE_NAMES_MAP.put(MODE_USB, "USB");
        MODE_NAMES_MAP.put(MODE_CW, "CW");
    }

    // ===== BANDWIDTH CONSTANTS (per AR-5000 RS232 spec) =====
    // Valid BW codes: 0=0.5K, 1=3K, 2=6K, 3=15K, 4=40K, 5=110K, 6=220K
    public static final int BW_AUTO = -1;
    public static final int BW_0_5K = 0;
    public static final int BW_3K = 1;
    public static final int BW_6K = 2;
    public static final int BW_15K = 3;
    public static final int BW_40K = 4;
    public static final int BW_110K = 5;
    public static final int BW_220K = 6;

    public static final String[] BW_NAMES = {
            "AUTO", "0.5K", "3K", "6K", "15K", "40K", "110K", "220K"
    };
    public static final int[] BW_CODES = {-1, 0, 1, 2, 3, 4, 5, 6};

    // SparseArray for O(1) BW name lookup
    private static final SparseArray<String> BW_NAMES_MAP = new SparseArray<>();
    static {
        BW_NAMES_MAP.put(BW_AUTO, "AUTO");
        BW_NAMES_MAP.put(BW_0_5K, "0.5K");
        BW_NAMES_MAP.put(BW_3K, "3K");
        BW_NAMES_MAP.put(BW_6K, "6K");
        BW_NAMES_MAP.put(BW_15K, "15K");
        BW_NAMES_MAP.put(BW_40K, "40K");
        BW_NAMES_MAP.put(BW_110K, "110K");
        BW_NAMES_MAP.put(BW_220K, "220K");
    }

    // ===== [ADDED] ATTENUATOR CONSTANTS (per spec: ATn) =====
    // ATn: n=0(0dB), 1(10dB), 2(20dB), F(AUTO)
    public static final int ATT_0DB = 0;
    public static final int ATT_10DB = 1;
    public static final int ATT_20DB = 2;
    public static final int ATT_AUTO = 3; // represented as 'F' in protocol

    private static final SparseArray<String> ATT_NAMES_MAP = new SparseArray<>();
    static {
        ATT_NAMES_MAP.put(ATT_0DB, "0dB");
        ATT_NAMES_MAP.put(ATT_10DB, "10dB");
        ATT_NAMES_MAP.put(ATT_20DB, "20dB");
        ATT_NAMES_MAP.put(ATT_AUTO, "AUTO");
    }

    // ===== [ADDED] ANTENNA CONSTANTS (per spec: ANn) =====
    // ANn: n=0(AUTO), 1-4(ANT#)
    public static final int ANT_AUTO = 0;
    public static final int ANT_1 = 1;
    public static final int ANT_2 = 2;
    public static final int ANT_3 = 3;
    public static final int ANT_4 = 4;

    private static final SparseArray<String> ANT_NAMES_MAP = new SparseArray<>();
    static {
        ANT_NAMES_MAP.put(ANT_AUTO, "AUTO");
        ANT_NAMES_MAP.put(ANT_1, "ANT 1");
        ANT_NAMES_MAP.put(ANT_2, "ANT 2");
        ANT_NAMES_MAP.put(ANT_3, "ANT 3");
        ANT_NAMES_MAP.put(ANT_4, "ANT 4");
    }

    // ===== [ADDED] HPF CONSTANTS (per spec: HPn) =====
    // HPn: n=0(0.05K), 1(0.2K), 2(0.3K), 3(0.4K)
    public static final int HPF_0_05K = 0;
    public static final int HPF_0_2K = 1;
    public static final int HPF_0_3K = 2;
    public static final int HPF_0_4K = 3;

    private static final SparseArray<String> HPF_NAMES_MAP = new SparseArray<>();
    static {
        HPF_NAMES_MAP.put(HPF_0_05K, "0.05K");
        HPF_NAMES_MAP.put(HPF_0_2K, "0.2K");
        HPF_NAMES_MAP.put(HPF_0_3K, "0.3K");
        HPF_NAMES_MAP.put(HPF_0_4K, "0.4K");
    }

    // ===== [ADDED] LPF CONSTANTS (per spec: LPn) =====
    // LPn: n=0(3K), 1(4K), 2(6K), 3(12K)
    public static final int LPF_3K = 0;
    public static final int LPF_4K = 1;
    public static final int LPF_6K = 2;
    public static final int LPF_12K = 3;

    private static final SparseArray<String> LPF_NAMES_MAP = new SparseArray<>();
    static {
        LPF_NAMES_MAP.put(LPF_3K, "3K");
        LPF_NAMES_MAP.put(LPF_4K, "4K");
        LPF_NAMES_MAP.put(LPF_6K, "6K");
        LPF_NAMES_MAP.put(LPF_12K, "12K");
    }

    // ===== [ADDED] CW PITCH CONSTANTS (per spec: CWn) =====
    // CWn: n=0(400Hz), 1(500Hz), 2(600Hz), 3(700Hz), 4(800Hz), 5(900Hz), 6(1000Hz), 7(1100Hz)
    public static final int CW_400HZ = 0;
    public static final int CW_500HZ = 1;
    public static final int CW_600HZ = 2;
    public static final int CW_700HZ = 3;
    public static final int CW_800HZ = 4;
    public static final int CW_900HZ = 5;
    public static final int CW_1000HZ = 6;
    public static final int CW_1100HZ = 7;

    private static final SparseArray<Integer> CW_FREQ_MAP = new SparseArray<>();
    static {
        CW_FREQ_MAP.put(CW_400HZ, 400);
        CW_FREQ_MAP.put(CW_500HZ, 500);
        CW_FREQ_MAP.put(CW_600HZ, 600);
        CW_FREQ_MAP.put(CW_700HZ, 700);
        CW_FREQ_MAP.put(CW_800HZ, 800);
        CW_FREQ_MAP.put(CW_900HZ, 900);
        CW_FREQ_MAP.put(CW_1000HZ, 1000);
        CW_FREQ_MAP.put(CW_1100HZ, 1100);
    }

    // ===== HELPER METHODS (optimized with SparseArray) =====

    public static String getModeName(int code) {
        String name = MODE_NAMES_MAP.get(code);
        return name != null ? name : MODE_NAMES[0];
    }

    public static int getModeCode(String name) {
        if (name == null) return MODE_AUTO;
        for (int i = 0; i < MODE_NAMES.length; i++) {
            if (MODE_NAMES[i].equalsIgnoreCase(name)) return MODE_CODES[i];
        }
        return MODE_AUTO;
    }

    public static boolean isValidMode(int code) {
        return code >= MODE_FM && code <= MODE_CW;
    }

    public static String getBwName(int code) {
        String name = BW_NAMES_MAP.get(code);
        return name != null ? name : BW_NAMES[0];
    }

    public static int getBwCode(String name) {
        if (name == null) return BW_AUTO;
        for (int i = 0; i < BW_NAMES.length; i++) {
            if (BW_NAMES[i].equalsIgnoreCase(name)) return BW_CODES[i];
        }
        return BW_AUTO;
    }

    public static boolean isValidBw(int code) {
        return code >= BW_0_5K && code <= BW_220K;
    }

    // [ADDED] Attenuator helpers
    public static String getAttenuatorName(int code) {
        String name = ATT_NAMES_MAP.get(code);
        return name != null ? name : "UNKNOWN";
    }

    public static String getAttenuatorParam(int code) {
        if (code == ATT_AUTO) return "F";
        if (code >= ATT_0DB && code <= ATT_20DB) return String.valueOf(code);
        return "0";
    }

    public static boolean isValidAttenuator(int code) {
        return code >= ATT_0DB && code <= ATT_AUTO;
    }

    // [ADDED] Antenna helpers
    public static String getAntennaName(int code) {
        String name = ANT_NAMES_MAP.get(code);
        return name != null ? name : "UNKNOWN";
    }

    public static boolean isValidAntenna(int code) {
        return code >= ANT_AUTO && code <= ANT_4;
    }

    // [ADDED] HPF helpers
    public static String getHpfName(int code) {
        String name = HPF_NAMES_MAP.get(code);
        return name != null ? name : "UNKNOWN";
    }

    public static boolean isValidHpf(int code) {
        return code >= HPF_0_05K && code <= HPF_0_4K;
    }

    // [ADDED] LPF helpers
    public static String getLpfName(int code) {
        String name = LPF_NAMES_MAP.get(code);
        return name != null ? name : "UNKNOWN";
    }

    public static boolean isValidLpf(int code) {
        return code >= LPF_3K && code <= LPF_12K;
    }

    // [ADDED] CW Pitch helpers
    public static String getCwPitchName(int code) {
        Integer freq = CW_FREQ_MAP.get(code);
        return freq != null ? freq + "Hz" : "UNKNOWN";
    }

    public static int getCwPitchFreq(int code) {
        Integer freq = CW_FREQ_MAP.get(code);
        return freq != null ? freq : 400;
    }

    public static boolean isValidCwPitch(int code) {
        return code >= CW_400HZ && code <= CW_1100HZ;
    }
}