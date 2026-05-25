package com.ar5000.audio;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import org.jtransforms.fft.FloatFFT_1D;

public class SpectrumView extends View {
    // FIX: Заменили WF_HIGH с красного (0xFFff4040) на бирюзовый (0xFF00ff88)
    private static final int BG=0xFF1a1a2e, GRID=0xFF3a3a5e, TR=0xFF00ff88, WF_LOW=0xFF000040, WF_HIGH=0xFF00ff88;
    private final Paint pTrace=new Paint(Paint.ANTI_ALIAS_FLAG), pGrid=new Paint(), pText=new Paint();
    private final FloatFFT_1D fft = new FloatFFT_1D(1024);
    private final float[] buf=new float[1024], mag=new float[512], spec=new float[512];
    private float centerHz=145000000f, spanHz=24000f;
    private boolean showWaterfall=true;
    private boolean hasValidData=false;

    public SpectrumView(Context c, AttributeSet a) {
        super(c,a);
        pTrace.setColor(TR);
        pTrace.setStyle(Paint.Style.STROKE);
        pTrace.setStrokeWidth(2f);
        pGrid.setColor(GRID);
        pText.setColor(Color.WHITE);
        pText.setTextSize(10f);
    }

    public void update(byte[] pcm, int off, int len) {
        if(pcm==null || len<2048) return;
        for(int i=0;i<1024;i++) {
            short s=(short)((pcm[off+i*4+1]<<8)|(pcm[off+i*4]&0xFF));
            buf[i]=s/32768f;
        }
        for(int i=0;i<1024;i++) buf[i]*=0.5f*(1f-(float)Math.cos(2*Math.PI*i/1023));
        fft.realForward(buf);
        for(int i=0;i<512;i++) {
            float r=buf[i*2], im=buf[i*2+1];
            mag[i]=(float)Math.sqrt(r*r+im*im);
            spec[i]=20f*(float)Math.log10(mag[i]+1e-10f);
        }
        hasValidData=true;
        postInvalidate();
    }

    protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(), h=getHeight();

        // FIX: Если нет данных - рисуем чёрный фон и уходим
        if(!hasValidData) {
            c.drawColor(Color.BLACK);
            pText.setColor(0xFF00ff00);
            c.drawText("NO SIGNAL", w/2f-35f, h/2f, pText);
            return;
        }

        c.drawColor(BG);

        if(showWaterfall) {
            for(int t=0;t<64;t++) {
                for(int f=0;f<512;f++) {
                    float db=spec[f];
                    // FIX: Ограничиваем диапазон, чтобы очень слабые сигналы не красились в красный
                    float n=Math.max(0f, Math.min(1f, (db+80f)/80f));
                    int col=Color.rgb(
                            (int)(Color.red(WF_LOW)+(Color.red(WF_HIGH)-Color.red(WF_LOW))*n),
                            (int)(Color.green(WF_LOW)+(Color.green(WF_HIGH)-Color.green(WF_LOW))*n),
                            (int)(Color.blue(WF_LOW)+(Color.blue(WF_HIGH)-Color.blue(WF_LOW))*n)
                    );
                    pGrid.setColor(col);
                    c.drawRect(f/512f*w, t/64f*h, (f+1)/512f*w+1, (t+1)/64f*h+1, pGrid);
                }
            }
        } else {
            Path p=new Path();
            for(int i=0;i<512;i++) {
                float x=i/512f*w;
                float y=h-(spec[i]+80f)/80f*h;
                if(i==0) p.moveTo(x,y);
                else p.lineTo(x,y);
            }
            c.drawPath(p, pTrace);
        }

        c.drawLine(w/2f, 0, w/2f, h*0.8f, pGrid);
        pText.setColor(Color.WHITE);
        c.drawText(String.format("%.3f MHz", centerHz/1e6f), w/2f+5f, 15f, pText);
    }

    public void setCenter(float hz) { centerHz=hz; }
    public void setSpan(float hz) { spanHz=hz; }
    public void setMode(boolean wf) { showWaterfall=wf; }
    public float[] getData() { return spec.clone(); }
}