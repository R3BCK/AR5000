// ResponseParser.java
package com.ar5000.core.protocol;

import com.ar5000.core.model.ReceiverState;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseParser {
    private Ar5000Controller.ResponseListener listener;
    private static final String TAG = "RX-PARSE";

    // ===== PATTERNS FOR SINGLE-LINE RESPONSES =====
    private static final Pattern FREQ_PATTERN = Pattern.compile("^([A-E]) RF (\\d+(?:\\.\\d+)?)$");
    private static final Pattern MODE_PATTERN = Pattern.compile("^MD (\\d+)$");
    private static final Pattern BW_PATTERN = Pattern.compile("^BW (\\d+)$");
    private static final Pattern STEP_PATTERN = Pattern.compile("^ST (\\d+)$");
    private static final Pattern ATT_PATTERN = Pattern.compile("^AT ([0-2F])$");
    private static final Pattern ANT_PATTERN = Pattern.compile("^AN ([0-4])$");
    private static final Pattern SQ_PATTERN = Pattern.compile("^RQ \\+?(\\d+)$");
    private static final Pattern AGC_PATTERN = Pattern.compile("^LM ([0-9A-Fa-f]{2})$");
    private static final Pattern ERR_PATTERN = Pattern.compile("^ERR");
    private static final Pattern BUSY_PATTERN = Pattern.compile("BUSY|OPEN|\\+\\d+");
    private static final Pattern CLEAR_PATTERN = Pattern.compile("CLEAR|CLOSED|(?<!\\+)\\d{3}(?!\\+)");

    public void setListener(Ar5000Controller.ResponseListener listener) {
        this.listener = listener;
    }

    public Ar5000Controller.ResponseListener getListener() {
        return listener;
    }

    /**
     * Parses a single-line response from AR5000.
     * Examples: "MD 0", "BW 3", "RQ +128", "VA RF 145000000"
     */
    public void parse(String line) {
        if (line == null || line.trim().isEmpty() || listener == null) return;

        String trimmed = line.trim();
        Log.d(TAG, "Single: " + trimmed);

        // Try single-line patterns first
        Matcher m = FREQ_PATTERN.matcher(trimmed);
        if (m.matches()) {
            listener.onFrequencyChanged(m.group(1), parseFreq(m.group(2)));
            return;
        }

        m = MODE_PATTERN.matcher(trimmed);
        if (m.matches()) {
            listener.onModeChanged(Integer.parseInt(m.group(1)));
            return;
        }

        m = BW_PATTERN.matcher(trimmed);
        if (m.matches()) {
            listener.onBandwidthChanged(Integer.parseInt(m.group(1)));
            return;
        }

        m = STEP_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Step changed - could notify if needed
            return;
        }

        m = ATT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Attenuator changed
            return;
        }

        m = ANT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Antenna changed
            return;
        }

        m = SQ_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Squelch level changed
            return;
        }

        m = AGC_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // AGC level changed
            return;
        }

        if (ERR_PATTERN.matcher(trimmed).find()) {
            listener.onError(trimmed);
            return;
        }

        if (BUSY_PATTERN.matcher(trimmed).find() && !CLEAR_PATTERN.matcher(trimmed).find()) {
            listener.onBusy(true);
            return;
        }
        if (CLEAR_PATTERN.matcher(trimmed).find() && !BUSY_PATTERN.matcher(trimmed).find()) {
            listener.onBusy(false);
            return;
        }

        // Fallback: pass raw status to listener
        listener.onRawStatus(trimmed);
    }

    /**
     * Parses a full status dump from RX command using direct calls.
     * Format according to PDF: VA RF145000000 ST5000 AU0 MD0 AT0 AN1 RQ128 LMFF TMTEXT
     * Logs every token for debugging. Does not swallow errors.
     */
    public void parseStatusDump(String raw, ReceiverState state) {
        if (raw == null || raw.trim().isEmpty() || state == null) return;
        Log.d(TAG, "Dump RAW: [" + raw + "]");

        String[] tokens = raw.trim().split("\\s+");
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            try {
                if (token.length() == 2 && token.charAt(0) == 'V' && "ABCDE".indexOf(token.charAt(1)) >= 0) {
                    state.setVfo(token.substring(1));
                } else if (token.startsWith("RF")) {
                    state.setFrequencyHz(parseFreq(token.substring(2)));
                } else if (token.startsWith("ST")) {
                    state.setStepHz(Long.parseLong(token.substring(2)));
                } else if (token.startsWith("AU")) {
                    state.setAutoMode("1".equals(token.substring(2)));
                } else if (token.startsWith("MD")) {
                    state.setModeCode(Integer.parseInt(token.substring(2)));
                } else if (token.startsWith("BW")) {
                    state.setBwCode(Integer.parseInt(token.substring(2)));
                } else if (token.startsWith("AT")) {
                    String v = token.substring(2);
                    state.setAttenuator("F".equalsIgnoreCase(v) ? 3 : Integer.parseInt(v));
                } else if (token.startsWith("AN")) {
                    state.setAntenna(Integer.parseInt(token.substring(2)));
                } else if (token.startsWith("RQ")) {
                    String v = token.substring(2);
                    if (v.startsWith("+")) { state.setSquelchOpen(true); v = v.substring(1); }
                    else { state.setSquelchOpen(false); }
                } else if (token.startsWith("LM") || token.startsWith("LC")) {
                    state.setAgcLevel(Integer.parseInt(token.substring(2), 16));
                } else if (token.startsWith("TM")) {
                    state.setLcdText(token.substring(2));
                } else {
                    Log.d(TAG, "Skip unknown token: " + token);
                }
            } catch (Exception e) {
                Log.w(TAG, "Parse fail token: " + token, e);
            }
        }
        Log.d(TAG, "Parsed state -> VFO:" + state.getVfo() + " F:" + state.getFrequencyHz() + " M:" + state.getModeCode());
    }

    /**
     * Helper: parse frequency from string (Hz or MHz format).
     * Examples: "145000000" -> 145000000, "145.000" -> 145000000
     */
    private long parseFreq(String f) {
        if (f == null || f.isEmpty()) return 0;
        if (f.contains(".")) {
            try { return (long) (Double.parseDouble(f) * 1_000_000); } catch (Exception e) { return 0; }
        }
        try { return Long.parseLong(f); } catch (Exception e) { return 0; }
    }

    public void notifyError(String msg) {
        if (listener != null) listener.onError(msg);
    }
}