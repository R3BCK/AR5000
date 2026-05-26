// ResponseParser.java
package com.ar5000.core.protocol;

import com.ar5000.core.model.ReceiverState;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseParser {

    private Ar5000Controller.ResponseListener listener;
    private static final String TAG = "RX-PARSE";

    // ===== PATTERNS FOR SINGLE-LINE RESPONSES (FIXED to match PDF spec) =====

    // [FIXED] Format: VxRFnnnnnnnnnn (no spaces, no brackets)
    private static final Pattern FREQ_PATTERN = Pattern.compile("^V([A-E])RF(\\d+)$");

    // Mode: MDn
    private static final Pattern MODE_PATTERN = Pattern.compile("^MD(\\d+)$");

    // Bandwidth: BWn
    private static final Pattern BW_PATTERN = Pattern.compile("^BW(\\d+)$");

    // Step: STnnnnnn (Hz)
    private static final Pattern STEP_PATTERN = Pattern.compile("^ST(\\d+)$");

    // Attenuator: ATn or ATF
    private static final Pattern ATT_PATTERN = Pattern.compile("^AT([0-2F])$");

    // Antenna: ANn
    private static final Pattern ANT_PATTERN = Pattern.compile("^AN([0-4])$");

    // [FIXED] Squelch: RQnnn or RQ+nnn (no space, + indicates squelch open)
    private static final Pattern SQ_PATTERN = Pattern.compile("^RQ(\\+?)(\\d+)$");

    // [FIXED] AGC Level: LMnn or LM%nn (no space, % indicates squelch closed)
    private static final Pattern AGC_PATTERN = Pattern.compile("^LM(%?)([0-9A-Fa-f]{2})$");

    // [ADDED] AGC Level Send status: LCn
    private static final Pattern AGC_SEND_PATTERN = Pattern.compile("^LC([01])$");

    // [ADDED] Version: VRxxx
    private static final Pattern VERSION_PATTERN = Pattern.compile("^VR(.+)$");

    // [ADDED] Memory read: MAnmm RF... MD... BW... (simplified, full parse in status dump)
    private static final Pattern MEM_READ_PATTERN = Pattern.compile("^MA(\\d)(\\d{2})\\s+RF(\\d+)\\s+MD(\\d+)\\s+BW(\\d+)$");

    // [ADDED] Text memo: TMnn <text>
    private static final Pattern MEMO_PATTERN = Pattern.compile("^TM(\\d{2})\\s*(.*)$");

    // [ADDED] CW Pitch: CWn
    private static final Pattern CW_PATTERN = Pattern.compile("^CW(\\d)$");

    // Error: unknown command
    // [FIXED] Protocol returns "?" not "ERR"
    private static final String UNKNOWN_CMD_RESPONSE = "?";

    public void setListener(Ar5000Controller.ResponseListener listener) {
        this.listener = listener;
    }

    public Ar5000Controller.ResponseListener getListener() {
        return listener;
    }

    /**
     * Parses a single-line response from AR5000.
     * Examples from PDF:
     *   "VARMF145000000" - frequency for VFO A
     *   "MD0" - mode FM
     *   "RQ+128" - squelch open, level 128
     *   "LMFF" - AGC level 0xFF, squelch open
     *   "LM%80" - AGC level 0x80, squelch closed
     *   "?" - unknown command
     */
    public void parse(String line) {
        if (line == null || line.trim().isEmpty() || listener == null) return;

        String trimmed = line.trim();
        Log.d(TAG, "Single: " + trimmed);

        // [FIXED] Handle unknown command response FIRST
        if (UNKNOWN_CMD_RESPONSE.equals(trimmed)) {
            listener.onError("Unknown command (?)");
            return;
        }

        Matcher m;

        // Frequency: VxRFnnnnnnnnnn - update specific VFO state
        m = FREQ_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String vfo = m.group(1);
            long freq = parseFreq(m.group(2));
            listener.onFrequencyChanged(vfo, freq);
            // [ADDED] Update VFO array state if available via listener
            updateVfoFrequency(vfo, freq);
            return;
        }

        // Mode: MDn - applies to active VFO only
        m = MODE_PATTERN.matcher(trimmed);
        if (m.matches()) {
            listener.onModeChanged(Integer.parseInt(m.group(1)));
            return;
        }

        // Bandwidth: BWn - applies to active VFO only
        m = BW_PATTERN.matcher(trimmed);
        if (m.matches()) {
            listener.onBandwidthChanged(Integer.parseInt(m.group(1)));
            return;
        }

        // Step: STnnnnnn - applies to active VFO only
        m = STEP_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Could notify step change if needed
            return;
        }

        // Attenuator: ATn or ATF - global setting
        m = ATT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Could notify attenuator change
            return;
        }

        // Antenna: ANn - global setting
        m = ANT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            // Could notify antenna change
            return;
        }

        // [FIXED] Squelch: RQ+nnn (open) or RQnnn (closed)
        m = SQ_PATTERN.matcher(trimmed);
        if (m.matches()) {
            boolean squelchOpen = "+".equals(m.group(1));
            int level = Integer.parseInt(m.group(2));
            // Could notify squelch state + level
            return;
        }

        // [FIXED] AGC Level: LMnn (squelch open) or LM%nn (squelch closed)
        m = AGC_PATTERN.matcher(trimmed);
        if (m.matches()) {
            boolean squelchOpen = !"%".equals(m.group(1));
            int level = Integer.parseInt(m.group(2), 16);
            // Could notify AGC level + squelch state
            return;
        }

        // [ADDED] AGC Level Send status: LC0 (off) or LC1 (on)
        m = AGC_SEND_PATTERN.matcher(trimmed);
        if (m.matches()) {
            boolean enabled = "1".equals(m.group(1));
            // Could notify AGC auto-send status
            return;
        }

        // [ADDED] Version: VRxxx
        m = VERSION_PATTERN.matcher(trimmed);
        if (m.matches()) {
            String version = m.group(1);
            // Could notify version string
            return;
        }

        // [ADDED] Memory read: MAnmm RF... MD... BW...
        m = MEM_READ_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int bank = Integer.parseInt(m.group(1));
            int channel = Integer.parseInt(m.group(2));
            long freq = parseFreq(m.group(3));
            int mode = Integer.parseInt(m.group(4));
            int bw = Integer.parseInt(m.group(5));
            // Could notify memory channel data
            return;
        }

        // [ADDED] Text memo: TMnn <text>
        m = MEMO_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int channel = Integer.parseInt(m.group(1));
            String text = m.group(2);
            // Could notify text memo content
            return;
        }

        // [ADDED] CW Pitch: CWn
        m = CW_PATTERN.matcher(trimmed);
        if (m.matches()) {
            int pitch = Integer.parseInt(m.group(1));
            // Could notify CW pitch
            return;
        }

        // Fallback: pass raw status to listener for debugging
        listener.onRawStatus(trimmed);
    }

    /**
     * Parses a full status dump from RX command.
     * Format from PDF:
     *   VA RF145000000 ST5000 AU0 MD0 AT0 AN1 RQ128 LMFF TMTEXT BW3
     * Notes:
     *   - Tokens are space-separated
     *   - VFO prefix: VA, VB, etc. (no space between letter and RF)
     *   - LM: hex level, squelch state encoded in presence of % in single-line mode
     *   - RQ: decimal level, + prefix indicates squelch open in single-line mode
     */
    public void parseStatusDump(String raw, ReceiverState state) {
        if (raw == null || raw.trim().isEmpty() || state == null) return;

        Log.d(TAG, "Dump RAW: [" + raw + "]");

        // [FIXED] Check for unknown command response in dump too
        if (UNKNOWN_CMD_RESPONSE.equals(raw.trim())) {
            if (listener != null) listener.onError("Unknown command in status dump (?)");
            return;
        }

        String[] tokens = raw.trim().split("\\s+");

        // Temporary variables to collect values before updating state
        String activeVfo = state.getVfo();
        long freq = state.getFrequencyHz();
        long step = state.getStepHz();
        int mode = state.getModeCode();
        int bw = state.getBwCode();

        for (String token : tokens) {
            if (token.isEmpty()) continue;
            try {
                // VFO prefix: VA, VB, etc.
                if (token.length() == 2 && token.charAt(0) == 'V' && "ABCDE".indexOf(token.charAt(1)) >= 0) {
                    activeVfo = token.substring(1);
                    state.setVfo(activeVfo);
                }
                // Frequency: RFnnnnnnnnnn
                else if (token.startsWith("RF")) {
                    freq = parseFreq(token.substring(2));
                    state.setFrequencyHz(freq);
                }
                // Step: STnnnnnn
                else if (token.startsWith("ST")) {
                    step = Long.parseLong(token.substring(2));
                    state.setStepHz(step);
                }
                // Auto mode: AU0/AU1
                else if (token.startsWith("AU")) {
                    state.setAutoMode("1".equals(token.substring(2)));
                }
                // Mode: MDn
                else if (token.startsWith("MD")) {
                    mode = Integer.parseInt(token.substring(2));
                    state.setModeCode(mode);
                }
                // Bandwidth: BWn
                else if (token.startsWith("BW")) {
                    int bwCode = Integer.parseInt(token.substring(2));
                    if (bwCode >= Ar5000Protocol.BW_0_5K && bwCode <= Ar5000Protocol.BW_220K) {
                        bw = bwCode;
                        state.setBwCode(bw);
                    }
                }
                // Attenuator: ATn or ATF
                else if (token.startsWith("AT")) {
                    String v = token.substring(2);
                    state.setAttenuator("F".equalsIgnoreCase(v) ? 3 : Integer.parseInt(v));
                }
                // Antenna: ANn
                else if (token.startsWith("AN")) {
                    state.setAntenna(Integer.parseInt(token.substring(2)));
                }
                // Squelch: RQnnn or RQ+nnn
                else if (token.startsWith("RQ")) {
                    String v = token.substring(2);
                    if (v.startsWith("+")) {
                        state.setSquelchOpen(true);
                        v = v.substring(1);
                    } else {
                        state.setSquelchOpen(false);
                    }
                }
                // AGC Level: LMnn (dump uses hex)
                else if (token.startsWith("LM")) {
                    String v = token.substring(2);
                    state.setAgcLevel(Integer.parseInt(v, 16));
                }
                // AGC Send status: LCn
                else if (token.startsWith("LC")) {
                    // Optional: state.setAgcAutoSend("1".equals(token.substring(2)));
                }
                // Text memo: TMnn<text>
                else if (token.startsWith("TM")) {
                    String v = token.substring(2);
                    if (v.length() >= 2) {
                        // Optional: parse channel and text
                    }
                }
                // CW Pitch: CWn
                else if (token.startsWith("CW")) {
                    int pitch = Integer.parseInt(token.substring(2));
                    // Optional: state.setCwPitchCode(pitch);
                }
                // Version: VRxxx
                else if (token.startsWith("VR")) {
                    state.setFirmwareVersion(token.substring(2).trim());
                }
                else {
                    Log.d(TAG, "Skip unknown token: " + token);
                }
            } catch (Exception e) {
                Log.w(TAG, "Parse fail token: " + token, e);
            }
        }

        // [ADDED] Update VFO array state with parsed values for active VFO
        if (activeVfo != null && !activeVfo.isEmpty()) {
            state.updateVfoFromResponse(activeVfo, freq, mode, bw, step);
        }

        Log.d(TAG, "Parsed state -> VFO:" + state.getVfo() + " F:" + state.getFrequencyHz() +
                " M:" + state.getModeCode() + " BW:" + state.getBwCode());
    }

    /**
     * Helper: update VFO frequency in state array (called from single-line parse).
     * Uses reflection-like approach via listener to avoid tight coupling.
     */
    private void updateVfoFrequency(String vfo, long freq) {
        // This is a placeholder - in practice, the listener (Ar5000Controller)
        // should have access to ReceiverState and call updateVfoFromResponse()
        // For now, we rely on the controller to handle this via onFrequencyChanged()
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