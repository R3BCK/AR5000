//PriorityMonitor.java
package com.ar5000.core.monitor;

import com.ar5000.core.protocol.Ar5000Controller;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.vfo.VfoManager;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PriorityMonitor {
    private final VfoManager vfoMgr;
    private final Ar5000Controller controller;
    private final Transport transport; // For backward compatibility
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private VfoManager.VfoId targetVfo = VfoManager.VfoId.A;
    private int pollIntervalSec = 5;
    private int signalThreshold = 0;
    private boolean switchOnSignal = false;
    private long switchDelayMs = 0;

    private AlertListener alertListener;

    // ===== NEW CONSTRUCTOR (preferred) =====
    public PriorityMonitor(VfoManager vfoMgr, Ar5000Controller controller) {
        this.vfoMgr = vfoMgr;
        this.controller = controller;
        this.transport = null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    // ===== OLD CONSTRUCTOR (backward compatibility for RadioApi) =====
    public PriorityMonitor(VfoManager vfoMgr, Transport transport) {
        this.vfoMgr = vfoMgr;
        this.transport = transport;
        this.controller = null;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }

    // ===== BACKWARD COMPATIBLE METHODS FOR RadioApi =====

    public void setListener(AlertListener l) {
        this.alertListener = l;
    }

    public void setTarget(VfoManager.VfoId vfo, int threshold, boolean switchOnSignal, long delayMs) {
        this.targetVfo = vfo;
        this.signalThreshold = threshold;
        this.switchOnSignal = switchOnSignal;
        this.switchDelayMs = delayMs;
    }

    public void start() {
        start(targetVfo, pollIntervalSec);
    }

    public void start(VfoManager.VfoId vfo, int intervalSeconds) {
        if (running.getAndSet(true)) return;
        targetVfo = vfo;
        pollIntervalSec = Math.max(1, Math.min(60, intervalSeconds));
        scheduler.scheduleAtFixedRate(this::poll, pollIntervalSec, pollIntervalSec, TimeUnit.SECONDS);
    }

    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
    }

    public void setPollInterval(int seconds) {
        pollIntervalSec = Math.max(1, Math.min(60, seconds));
    }

    // ===== POLLING LOGIC =====

    private void poll() {
        if (!running.get()) return;

        VfoManager.VfoId active = vfoMgr.getActive();
        if (targetVfo == active) return;

        try {
            // Select target VFO and query signal strength
            if (controller != null) {
//                controller.selectVfo(targetVfo.name());
                // Note: signal meter query would need separate method in controller
                // For now, we simulate by checking state via vfoMgr if available
            } else if (transport != null && transport.isConnected()) {
                // Fallback to direct transport for backward compatibility
                String cmd = "XX" + targetVfo.name() + "\r\n" + targetVfo.name() + "SM\r\n";
                transport.write(cmd.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            }

            // Notify listener if signal detected (simplified - real impl would parse SM response)
            if (alertListener != null) {
                // In real impl, signal strength would come from parsed SM response
                // For now, we just notify on every poll if listener exists
                // alertListener.onSignal(targetVfo, signalStrength, System.currentTimeMillis());
            }

        } catch (Exception ignored) {}
    }

    // ===== ALERT LISTENER INTERFACE (unchanged for backward compatibility) =====

    public interface AlertListener {
        void onSignal(VfoManager.VfoId v, int lvl, long ts);
        void onSwitch(VfoManager.VfoId v);
    }
}