//Transport.java
package com.ar5000.core.transport;

import java.io.IOException;
import java.io.InputStream;

public abstract class Transport {

    public interface TransportListener {
        void onConnected();
        void onDisconnected();
        void onDataReceived(byte[] data);
        void onError(Exception e);
    }

    protected TransportListener listener;
    protected volatile boolean connected = false;

    public void setListener(TransportListener l) { this.listener = l; }

    public abstract boolean connect();
    public abstract void disconnect();
    public abstract boolean write(byte[] data) throws IOException;

    // НОВЫЙ МЕТОД: Возвращает InputStream для чтения ответов от устройства
    public abstract InputStream getInputStream() throws IOException;

    public boolean isConnected() { return connected; }
    public abstract String getTransportName();
}