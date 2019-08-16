package com.example.pecanventures.bluetoothdiscoveryexample;

import java.io.IOException;

public interface ConnectionInterface {
    public void sendCommand(String cmd) throws IOException;
    public void sendCommand(byte byteData[]) throws IOException;

    public String readLine() throws IOException;
}
