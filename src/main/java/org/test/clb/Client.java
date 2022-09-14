package org.test.clb;// A Java program for a Client

import java.net.*;
import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client {
    private final Logger log = LoggerFactory.getLogger(getClass());
    // initialize socket and input output streams
    private Socket socket = null;
    private DataInputStream input = null;
    private DataOutputStream out = null;

    // constructor to put ip address and port
    public Client(String address, int port) {
        // establish a connection
        try {
            socket = new Socket(address, port);
            // sends output to the socket
            out = new DataOutputStream(socket.getOutputStream());
        } catch (UnknownHostException u) {
            log.info(u.toString());
        } catch (IOException i) {
            log.info(i.toString());
        }
    }

    public void sendData(String data) {
        try {
            out.writeUTF(data);
        } catch (IOException i) {
            log.info(i.toString());
        }

    }

    public void connectionClose() {
        // close the connection
        try {
            input.close();
            out.close();
            socket.close();
        } catch (IOException i) {
            log.info(i.toString());
        }
    }

}