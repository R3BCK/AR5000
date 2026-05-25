//Ar5000Protocol.java
package com.ar5000.core.protocol;

public final class Ar5000Protocol {
    private Ar5000Protocol() {}

    // ===== MODE CONSTANTS =====
    public static final int MODE_AUTO = -1;
    public static final int MODE_FM = 0;
    public static final int MODE_AM = 1;
    public static final int MODE_LSB = 2;
    public static final int MODE_USB = 3;
    public static final int MODE_CW = 4;
    public static final int MODE_SAM = 5;
    public static final int MODE_SAL = 6;
    public static final int MODE_SAH = 7;

    public static final String[] MODE_NAMES = {
            "AUTO", "FM", "AM", "LSB", "USB", "CW", "SAM", "SAL", "SAH"
    };
    public static final int[] MODE_CODES = {-1, 0, 1, 2, 3, 4, 5, 6, 7};

    // ===== BANDWIDTH CONSTANTS =====
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

    // ===== HELPER METHODS =====
    public static String getModeName(int code) {
        for (int i = 0; i < MODE_CODES.length; i++) {
            if (MODE_CODES[i] == code) return MODE_NAMES[i];
        }
        return MODE_NAMES[0];
    }

    public static int getModeCode(String name) {
        for (int i = 0; i < MODE_NAMES.length; i++) {
            if (MODE_NAMES[i].equals(name)) return MODE_CODES[i];
        }
        return MODE_CODES[0];
    }

    public static String getBwName(int code) {
        for (int i = 0; i < BW_CODES.length; i++) {
            if (BW_CODES[i] == code) return BW_NAMES[i];
        }
        return BW_NAMES[0];
    }

    public static int getBwCode(String name) {
        for (int i = 0; i < BW_NAMES.length; i++) {
            if (BW_NAMES[i].equals(name)) return BW_CODES[i];
        }
        return BW_CODES[0];
    }
}