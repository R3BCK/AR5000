// UsbSerialTransport.java
package com.ar5000.core.transport;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import java.nio.charset.StandardCharsets;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class UsbSerialTransport extends Transport {

    private static final String TAG = "USB-Transport";

    private final UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection usbConnection;
    private UsbSerialDriver driver;
    private UsbSerialPort serialPort;
    private ExecutorService ioExecutor;
    private SerialPortInputStream inputStream;
    private final AtomicBoolean reading = new AtomicBoolean(false);

    private int baudRate = 9600;
    private int dataBits = 8;
    private int stopBits = UsbSerialPort.STOPBITS_1;
    private int parity = UsbSerialPort.PARITY_NONE;

    public UsbSerialTransport(UsbManager usbManager) {
        this.usbManager = usbManager;
        this.ioExecutor = Executors.newSingleThreadExecutor();
    }

    public void setBaudRate(int baudRate) {
        this.baudRate = baudRate;
        updatePortParameters();
    }

    private void updatePortParameters() {
        if (serialPort != null && connected) {
            try {
                serialPort.setParameters(baudRate, dataBits, stopBits, parity);
                Log.i(TAG, "Port parameters updated: " + baudRate + " 8N1");
            } catch (IOException e) {
                Log.e(TAG, "Failed to update port parameters: " + e.getMessage());
            }
        }
    }

    @Override
    public boolean connect() {
        if (connected) {
            Log.i(TAG, "Already connected");
            return true;
        }

        // FIX: Recreate executor if it was shut down
        if (ioExecutor == null || ioExecutor.isShutdown() || ioExecutor.isTerminated()) {
            ioExecutor = Executors.newSingleThreadExecutor();
            Log.i(TAG, "Recreated ioExecutor");
        }

        // 1. Find supported device
        device = findSupportedDevice();
        if (device == null) {
            Log.e(TAG, "No supported USB serial device found");
            if (listener != null) listener.onError(new IOException("USB serial device not found"));
            return false;
        }
        Log.i(TAG, "Found device: " + device.getDeviceName() +
                " (VID:0x" + Integer.toHexString(device.getVendorId()) +
                " PID:0x" + Integer.toHexString(device.getProductId()) + ")");

        // 2. Check permissions
        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "No USB permission for device");
            if (listener != null) listener.onError(new IOException("USB permission not granted"));
            return false;
        }
        Log.i(TAG, "USB permission granted");

        // 3. Open device connection
        usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) {
            Log.e(TAG, "Failed to open UsbDeviceConnection");
            if (listener != null) listener.onError(new IOException("Cannot open USB device"));
            return false;
        }
        Log.i(TAG, "UsbDeviceConnection opened");

        // 4. Find driver via standard prober
        driver = UsbSerialProber.getDefaultProber().probeDevice(device);

        // FIX: Force Ch34xSerialDriver for CH340 devices if wrong driver selected
        if (driver != null && device.getVendorId() == 0x1A86) {
            String driverName = driver.getClass().getSimpleName();
            if (driverName.contains("CdcAcm") || driverName.contains("Cdc")) {
                Log.w(TAG, "Wrong driver selected (" + driverName + ") for CH340, forcing Ch34xSerialDriver...");
                try {
                    Class<?> ch34xClass = Class.forName("com.hoho.android.usbserial.driver.Ch34xSerialDriver");
                    java.lang.reflect.Constructor<?> ctor = ch34xClass.getConstructor(UsbDevice.class);
                    driver = (UsbSerialDriver) ctor.newInstance(device);
                    Log.i(TAG, "Successfully forced Ch34xSerialDriver");
                } catch (Exception e) {
                    Log.e(TAG, "Could not force Ch34xSerialDriver: " + e.getMessage());
                }
            }
        }

        if (driver == null) {
            Log.e(TAG, "No driver found for device");
            usbConnection.close();
            if (listener != null) listener.onError(new IOException("No driver for USB device"));
            return false;
        }
        Log.i(TAG, "Selected driver: " + driver.getClass().getSimpleName());

        // 5. Get port
        List<UsbSerialPort> ports = driver.getPorts();
        if (ports.isEmpty()) {
            Log.e(TAG, "No serial ports");
            usbConnection.close();
            if (listener != null) listener.onError(new IOException("No serial ports"));
            return false;
        }
        serialPort = ports.get(0);
        Log.i(TAG, "Using port: " + serialPort.getPortNumber());

        // 6. Open port
        try {
            serialPort.open(usbConnection);
            serialPort.setParameters(baudRate, dataBits, stopBits, parity);
            serialPort.setDTR(true);
            serialPort.setRTS(true);
            Log.i(TAG, "Serial port opened: " + baudRate + " 8N1");
        } catch (IOException e) {
            Log.e(TAG, "Failed to open port: " + e.getMessage());
            try { usbConnection.close(); } catch (Exception ignored) {}
            if (listener != null) listener.onError(e);
            return false;
        }

        // 7. Start reading
        inputStream = new SerialPortInputStream(serialPort);
        connected = true;
        reading.set(true);

        // FIX: Check executor state before executing
        if (!ioExecutor.isShutdown()) {
            ioExecutor.execute(this::readLoop);
        } else {
            Log.e(TAG, "Executor is shutdown, cannot start readLoop");
            return false;
        }

        Log.i(TAG, "USB connection established");
        if (listener != null) listener.onConnected();
        return true;
    }

    private UsbDevice findSupportedDevice() {
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            int vid = d.getVendorId();
            int pid = d.getProductId();
            String name = d.getDeviceName();

            // FIX: Explicitly skip USB-to-Ethernet adapters
            if (name != null && (name.contains("LAN") || name.contains("Ethernet") || name.contains("cdc_ether"))) {
                Log.i(TAG, "Skipping Ethernet adapter: " + name);
                continue;
            }

            // CH340/CH341
            if (vid == 0x1A86) {
                Log.i(TAG, "Found CH340 device: PID=0x" + Integer.toHexString(pid));
                return d;
            }
            // CP210x
            if (vid == 0x10C4) return d;
            // FTDI
            if (vid == 0x0403) return d;
        }
        return null;
    }

    @Override
    public void disconnect() {
        Log.i(TAG, "Disconnecting...");
        connected = false;
        reading.set(false);

        if (serialPort != null) {
            try { serialPort.close(); } catch (IOException ignored) {}
            serialPort = null;
        }
        if (usbConnection != null) {
            usbConnection.close();
            usbConnection = null;
        }
        driver = null;
        inputStream = null;
        if (listener != null) listener.onDisconnected();
        Log.i(TAG, "Disconnected");
    }

    @Override
    public boolean write(byte[] data) throws IOException {
        if (!connected || serialPort == null) throw new IOException("Not connected");
        if (data == null || data.length == 0) return true;

        // FIX: Log what we're sending for debugging
        String debugStr = new String(data, StandardCharsets.US_ASCII).trim();
        Log.i("USB-TX", ">>> Sending: [" + debugStr + "] (" + data.length + " bytes)");

        serialPort.write(data, 3000);
        return true;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected || inputStream == null) throw new IOException("Not connected");
        return inputStream;
    }

    @Override
    public String getTransportName() {
        return device != null ? "USB:" + device.getDeviceName() : "USB:unknown";
    }

    private void readLoop() {
        byte[] buffer = new byte[256];
        while (reading.get() && connected && serialPort != null) {
            try {
                int len = serialPort.read(buffer, 500);
                Log.d("USB-RX-DEBUG", "read() returned: " + len);

                if (len > 0) {
                    byte[] data = new byte[len];
                    System.arraycopy(buffer, 0, data, 0, len);
                    String response = new String(data, StandardCharsets.US_ASCII).trim();
                    Log.i("USB-RX", "<<< Received: [" + response + "]");
                    if (listener != null) {
                        listener.onDataReceived(data);
                    }
                } else if (len == 0) {
                    Log.d("USB-RX-DEBUG", "Timeout (no data in 500ms)");
                } else if (len < 0) {
                    Log.d("USB-RX-DEBUG", "End of stream");
                }
            } catch (IOException e) {
                Log.e("USB-RX-DEBUG", "Read error: " + e.getMessage());
                if (listener != null) listener.onError(e);
                break;
            }
        }
    }

    private static class SerialPortInputStream extends InputStream {
        private final UsbSerialPort port;
        private final byte[] singleByte = new byte[1];
        private final byte[] internalBuffer = new byte[1024];
        private int bufferPos = 0, bufferLen = 0;

        SerialPortInputStream(UsbSerialPort port) { this.port = port; }

        @Override
        public int read() throws IOException {
            int n = port.read(singleByte, 1000);
            return (n > 0) ? (singleByte[0] & 0xFF) : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (b == null) throw new NullPointerException();
            if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
            if (len == 0) return 0;
            if (bufferLen > 0 && bufferPos < bufferLen) {
                int toCopy = Math.min(len, bufferLen - bufferPos);
                System.arraycopy(internalBuffer, bufferPos, b, off, toCopy);
                bufferPos += toCopy;
                return toCopy;
            }
            int n = port.read(internalBuffer, 1000);
            if (n <= 0) return -1;
            bufferPos = 0;
            bufferLen = n;
            int toCopy = Math.min(len, n);
            System.arraycopy(internalBuffer, 0, b, off, toCopy);
            bufferPos = toCopy;
            return toCopy;
        }

        @Override
        public int available() { return bufferLen - bufferPos; }

        @Override
        public void close() { bufferLen = 0; bufferPos = 0; }
    }
}