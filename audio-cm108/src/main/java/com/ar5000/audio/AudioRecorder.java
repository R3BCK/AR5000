package com.ar5000.audio;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecorder {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean rec = new AtomicBoolean(false);
    private FileOutputStream fos;
    private File curFile;
    private long startMs;
    private long totalBytes;
    private AudioPreset preset;

    public void setPreset(AudioPreset p) {
        this.preset = p;
    }

    public boolean start(File dir) {
        if (rec.get()) return false;
        try {
            String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
            curFile = new File(dir, "AR5000_" + ts + ".wav");
            fos = new FileOutputStream(curFile);
            writeHeader(0);
            startMs = System.currentTimeMillis();
            totalBytes = 0;
            rec.set(true);
            return true;
        } catch (IOException e) {
            android.util.Log.e("AudioRecorder", "Failed to start recording", e);
            return false;
        }
    }

    public void write(byte[] d, int off, int len) {
        if (!rec.get() || fos == null) return;
        io.execute(() -> {
            try {
                fos.write(d, off, len);
                totalBytes += len;
            } catch (IOException ignored) {
                android.util.Log.w("AudioRecorder", "Write failed", ignored);
            }
        });
    }

    public void addMarker(String label) {
        if (!rec.get() || fos == null) return;
        io.execute(() -> {
            try {
                long ts = System.currentTimeMillis();
                byte[] lbl = label.getBytes("UTF-8");
                ByteBuffer buf = ByteBuffer.allocate(16 + lbl.length).order(ByteOrder.LITTLE_ENDIAN);
                buf.putInt(0x4D41524B);  // "MARK"
                buf.putLong(ts);
                buf.putInt(lbl.length);
                buf.put(lbl);
                fos.write(buf.array());
                totalBytes += buf.array().length;
            } catch (Exception ignored) {
                android.util.Log.w("AudioRecorder", "Marker write failed", ignored);
            }
        });
    }

    public File stop() {
        if (!rec.get()) return null;
        rec.set(false);

        // FIX: Правильная обработка исключений при ожидании завершения задач
        try {
            Future<?> f = io.submit(() -> {});
            f.get();  // Ждём завершения всех задач в очереди
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            android.util.Log.w("AudioRecorder", "Interrupted while waiting for write completion", e);
        } catch (java.util.concurrent.ExecutionException e) {
            android.util.Log.w("AudioRecorder", "Error waiting for write completion", e);
        }

        try {
            if (fos != null) {
                // Обновляем заголовок WAV с реальным размером данных
                updateHeader(totalBytes);
                fos.close();
                fos = null;
            }
        } catch (IOException e) {
            android.util.Log.e("AudioRecorder", "Failed to close file", e);
        }
        return curFile;
    }

    public boolean isRecording() {
        return rec.get();
    }

    private void writeHeader(long dataSize) throws IOException {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(0x52494646);  // "RIFF"
        h.putInt((int) (36 + dataSize));  // ChunkSize
        h.putInt(0x57415645);  // "WAVE"
        h.putInt(0x666d7420);  // "fmt "
        h.putInt(16);  // Subchunk1Size
        h.putShort((short) 1);  // AudioFormat (PCM)
        h.putShort((short) (preset != null ? preset.getChannels() : 2));  // NumChannels
        h.putInt(preset != null ? preset.getSampleRate() : 48000);  // SampleRate
        h.putInt((preset != null ? preset.getSampleRate() : 48000) * (preset != null ? preset.getChannels() : 2) * 2);  // ByteRate
        h.putShort((short) ((preset != null ? preset.getChannels() : 2) * 2));  // BlockAlign
        h.putShort((short) 16);  // BitsPerSample
        h.putInt(0x64617461);  // "data"
        h.putInt((int) dataSize);  // Subchunk2Size
        fos.write(h.array());
    }

    private void updateHeader(long dataSize) throws IOException {
        // Перезаписываем заголовок с актуальным размером
        if (curFile == null || !curFile.exists()) return;

        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(curFile, "rw")) {
            raf.seek(4);  // Позиция ChunkSize
            raf.writeInt((int) (36 + dataSize));
            raf.seek(40);  // Позиция Subchunk2Size
            raf.writeInt((int) dataSize);
        }
    }

    public void release() {
        if (rec.get()) stop();
        io.shutdown();
    }
}