//IpTransport.java
package com.ar5000.core.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class IpTransport extends Transport {

    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private ExecutorService ioExecutor;

    public IpTransport(String host, int port) {
        this.host = host;
        this.port = port;
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    public IpTransport(String host) {
        this(host, 2323);
    }

    @Override
    public boolean connect() {
        try {
            disconnect(); // Ensure clean state before reconnect
            socket = new Socket(host, port);
            socket.setSoTimeout(0); // Blocking mode for continuous reading
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            connected = true;
            ioExecutor.execute(this::readLoop);
            if (listener != null) listener.onConnected();
            return true;
        } catch (IOException e) {
            connected = false;
            if (listener != null) listener.onError(e);
            return false;
        }
    }

    @Override
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}
        if (listener != null) listener.onDisconnected();
    }

    @Override
    public boolean write(byte[] data) throws IOException {
        if (!connected || outputStream == null) {
            throw new IOException("Not connected");
        }
        outputStream.write(data);
        outputStream.flush();
        return true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected || inputStream == null) {
            throw new IOException("Not connected");
        }
        return inputStream;
    }

    @Override
    public String getTransportName() {
        return "IP:" + host + ":" + port;
    }

    private void readLoop() {
        byte[] buffer = new byte[256];
        while (connected && socket != null && !socket.isClosed()) {
            try {
                int len = inputStream.read(buffer);
                if (len > 0 && listener != null) {
                    byte[] data = new byte[len];
                    System.arraycopy(buffer, 0, data, 0, len);
                    listener.onDataReceived(data);
                }
            } catch (IOException e) {
                if (connected) {
                    connected = false;
                    if (listener != null) listener.onError(e);
                }
                break;
            }
        }
    }
}