// VirtualPanelController.java
package com.ar5000.core.panel;

import com.ar5000.core.protocol.Ar5000Command;
import com.ar5000.core.protocol.CommandFactory;
import com.ar5000.core.transport.Transport;
import com.ar5000.core.model.ReceiverState;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class VirtualPanelController {
    private final ReceiverState state;
    private final Queue<PendingCmd> queue;
    private Transport transport;
    private UpdateListener listener;

    public interface UpdateListener {
        void onStateChanged(ReceiverState s);
        void onCmdSent(String h, String p);
        void onLog(String m);
    }

    public VirtualPanelController() {
        this.state = new ReceiverState();
        this.queue = new ConcurrentLinkedQueue<>();
    }

    public void attachTransport(Transport t) {
        this.transport = t;
        t.setListener(new TransportAdapter());
    }

    public void setListener(UpdateListener l) {
        this.listener = l;
    }

    public void onDialRotated(boolean coarse, float delta) {
        long step = 5000;
        if (!coarse) step = 500;
        long deltaHz = (long)(delta * step / 15f);
        long newFreq = Math.max(10000L, Math.min(2600000000L, state.getFrequencyHz() + deltaHz));
        queueCommand(CommandFactory.setFrequency(state.isRemoteMode() ? "VB" : "VA", newFreq));
        state.setFrequencyHz(newFreq);
        triggerStateUpdate();
    }

    public void onKeyCommand(String cmd, String params) {
        Ar5000Command c = null;
        switch (cmd) {
            case "MODE":
                c = CommandFactory.setMode(state.getModeCode() < 5 ? state.getModeCode() + 1 : 0);
                break;
            case "BW":
                c = CommandFactory.setBandwidth(state.getBwCode() < 6 ? state.getBwCode() + 1 : 0);
                break;
            case "ATT":
                c = CommandFactory.setAttenuator(0);
                break;
            case "SCAN":
                // [FIXED] getBank() -> getScanBank() (метод в ReceiverState)
                c = CommandFactory.startScan(state.getScanBank());
                break;
        }
        if (c != null) {
            queueCommand(c);
            triggerStateUpdate();
        }
    }

    private void queueCommand(Ar5000Command c) {
        if (c != null) queue.offer(new PendingCmd(c));
        if (transport != null && transport.isConnected()) processQueue();
    }

    private void processQueue() {
        while (!queue.isEmpty() && transport.isConnected()) {
            PendingCmd p = queue.peek();
            if (p.isTimedOut()) {
                queue.poll();
                continue;
            }
            if (!p.sent) {
                try {
                    transport.write(p.cmd.buildPacket());
                    p.sent = true;
                    if (listener != null) listener.onCmdSent(p.cmd.getHeader(), p.cmd.getParameters().toString());
                } catch (Exception e) {
                    // Ignore write errors, will be handled by transport listener
                }
            }
            break;
        }
    }

    // FIX: Переименовали notify() -> triggerStateUpdate(), чтобы не конфликтовать с Object.notify()
    private void triggerStateUpdate() {
        if (listener != null) {
            // FIX: Используем state.copy() вместо state.clone()
            listener.onStateChanged(state.copy());
        }
    }

    private class TransportAdapter implements Transport.TransportListener {
        public void onConnected() {
            queueCommand(CommandFactory.getVersion());
            queueCommand(CommandFactory.getFrequency("VA"));
        }
        public void onDisconnected() {
            state.setRemoteMode(false);
            triggerStateUpdate();
        }
        public void onDataReceived(byte[] d) {
            String r = new String(d, java.nio.charset.StandardCharsets.US_ASCII).trim();
            if (r.startsWith("RF")) {
                try { state.setFrequencyHz(Long.parseLong(r.substring(2))); } catch (Exception ignored) {}
            } else if (r.startsWith("MD")) {
                try { state.setModeCode(Integer.parseInt(r.substring(2))); } catch (Exception ignored) {}
            } else if (r.endsWith("SM")) {
                try { state.setSignalStrength(Integer.parseInt(r.replace("SM", "").trim())); } catch (Exception ignored) {}
            }
            if (!queue.isEmpty()) queue.poll().complete = true;
            triggerStateUpdate();
        }
        public void onError(Exception e) {
            if (listener != null) listener.onLog("Transport error: " + e.getMessage());
        }
    }

    private static class PendingCmd {
        final Ar5000Command cmd;
        long sentTime;
        boolean sent, complete;
        PendingCmd(Ar5000Command c) { cmd = c; sentTime = System.currentTimeMillis(); }
        boolean isTimedOut() { return sent && !complete && System.currentTimeMillis() - sentTime > 2000; }
    }
}