//VfoManager.java
package com.ar5000.core.vfo;
import com.ar5000.core.model.ReceiverState;
import java.util.HashMap; import java.util.Map;

public class VfoManager {
    public enum VfoId { A, B, C, D, E }
    private final Map<VfoId, ReceiverState> states = new HashMap<>();
    private VfoId active = VfoId.A;
    private VfoChangeListener listener;

    public interface VfoChangeListener { void onSelected(VfoId v); void onStateChanged(VfoId v, ReceiverState s); }

    public VfoManager() { for(VfoId id : VfoId.values()) states.put(id, new ReceiverState()); }
    public VfoId getActive() { return active; }
    public boolean select(VfoId v) { active=v; if(listener!=null) listener.onSelected(v); return true; }
    public void update(VfoId v, ReceiverState s) { ReceiverState cur=states.get(v); if(cur!=null && s!=null) { if(s.getFrequencyHz()>0) cur.setFrequencyHz(s.getFrequencyHz()); if(s.getModeCode()>=0) cur.setModeCode(s.getModeCode()); if(s.getBwCode()>=0) cur.setBwCode(s.getBwCode()); cur.setSignalStrength(s.getSignalStrength()); if(listener!=null && v==active) listener.onStateChanged(v, cur); } }
    public ReceiverState getState(VfoId v) { return states.get(v); }
    public void setListener(VfoChangeListener l) { this.listener=l; }
}