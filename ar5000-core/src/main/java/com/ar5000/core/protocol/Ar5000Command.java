//Ar5000Command.java
package com.ar5000.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Ar5000Command {
    protected static final String DELIMITER = "\r\n";
    protected final String header;
    protected final List<String> parameters;
    protected final boolean isRequest;
    protected final boolean noSpace;

    public Ar5000Command(String header, boolean isRequest) {
        this(header, isRequest, false);
    }

    public Ar5000Command(String header, boolean isRequest, boolean noSpace) {
        this.header = header.toUpperCase();
        this.parameters = new ArrayList<>();
        this.isRequest = isRequest;
        this.noSpace = noSpace;
    }

    public Ar5000Command addParam(String param) {
        if (param != null && !param.isEmpty()) parameters.add(param);
        return this;
    }

    public byte[] buildPacket() {
        StringBuilder sb = new StringBuilder();
        sb.append(header);
        if (noSpace) {
            for (String p : parameters) sb.append(p);
        } else {
            for (String p : parameters) sb.append(' ').append(p);
        }
        sb.append(DELIMITER);
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    public String getHeader() { return header; }
    public boolean isRequest() { return isRequest; }
    public List<String> getParameters() { return new ArrayList<>(parameters); }
}