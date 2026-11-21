package com.luna.phonemiccam;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;

/** Small LAN server. Audio uses raw PCM WebSocket frames to avoid media-element buffering. */
final class StreamServer {
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final Context context;
    private final List<AudioPeer> audioClients = Collections.synchronizedList(new ArrayList<>());
    private volatile ServerSocket listener;
    private volatile boolean running;
    private volatile byte[] latestJpeg;
    private volatile boolean cameraActive;
    private int port;

    private final class AudioPeer {
        final Socket socket;
        final DataOutputStream out;
        final LinkedBlockingDeque<byte[]> queue = new LinkedBlockingDeque<>(6);
        volatile boolean open = true;
        AudioPeer(Socket socket) throws IOException { this.socket = socket; this.out = new DataOutputStream(socket.getOutputStream()); }
        void start() { new Thread(() -> { try { while (running && open) { byte[] pcm = queue.takeFirst(); out.write(0x82); int n=pcm.length; if(n<126)out.write(n);else{out.write(126);out.write((n>>>8)&255);out.write(n&255);} out.write(pcm); out.flush(); } } catch(Exception ignored) { } finally { close(); } }, "luna-ws-audio").start(); }
        void offer(byte[] pcm) { if (!queue.offerLast(pcm)) { queue.pollFirst(); queue.offerLast(pcm); } }
        void close() { if (!open) return; open=false; audioClients.remove(this); try { socket.close(); } catch(IOException ignored) {} }
    }

    StreamServer(Context context) { this.context = context.getApplicationContext(); }

    synchronized void start() throws IOException {
        if (running) return;
        listener = new ServerSocket(0, 16, InetAddress.getByName("0.0.0.0"));
        listener.setReuseAddress(true);
        port = listener.getLocalPort();
        running = true;
        Thread accept = new Thread(() -> {
            while (running) {
                try {
                    Socket socket = listener.accept();
                    socket.setTcpNoDelay(true);
                    socket.setSendBufferSize(4096);
                    socket.setSoTimeout(8000);
                    new Thread(() -> handle(socket), "luna-client").start();
                } catch (IOException e) { if (running) e.printStackTrace(); }
            }
        }, "luna-http");
        accept.start();
    }

    int getPort() { return port; }
    boolean isRunning() { return running; }
    int getAudioClientCount() { return audioClients.size(); }
    void setCameraActive(boolean active) { cameraActive = active; }
    void publishJpeg(byte[] image) { latestJpeg = image; }

    void publishPcm(byte[] pcm) {
        synchronized (audioClients) { for (AudioPeer peer : audioClients) peer.offer(pcm); }
    }

    private void handle(Socket socket) {
        try {
            InputStream input = new BufferedInputStream(socket.getInputStream());
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int state = 0, b;
            while (head.size() < 8192 && (b = input.read()) != -1) {
                head.write(b);
                if ((state == 0 || state == 2) && b == '\r') state++;
                else if ((state == 1 || state == 3) && b == '\n') { if (++state == 4) break; }
                else state = b == '\r' ? 1 : 0;
            }
            String request = head.toString("UTF-8");
            String[] lines = request.split("\r\n");
            if (lines.length == 0) { socket.close(); return; }
            String[] first = lines[0].split(" ");
            if (first.length < 2) { socket.close(); return; }
            String path = first[1].split("\\?")[0];
            if (path.equals("/audio.ws")) { websocket(socket, lines); return; }
            if (path.equals("/video.mjpg")) { mjpeg(socket); return; }
            if (!path.equals("/") && !path.equals("/index.html")) {
                writeResponse(socket.getOutputStream(), 404, "text/plain", "Not found"); socket.close(); return;
            }
            byte[] html;
            try (InputStream asset = context.getAssets().open("obs.html")) { html = readAll(asset); }
            writeResponse(socket.getOutputStream(), 200, "text/html; charset=utf-8", html);
            socket.close();
        } catch (Exception ignored) { try { socket.close(); } catch (IOException ignoredAgain) {} }
    }

    private void websocket(Socket socket, String[] lines) throws Exception {
        String key = null;
        for (String line : lines) if (line.toLowerCase(Locale.US).startsWith("sec-websocket-key:")) key = line.substring(line.indexOf(':') + 1).trim();
        if (key == null) { socket.close(); return; }
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).getBytes(StandardCharsets.US_ASCII)));
        OutputStream raw = socket.getOutputStream();
        raw.write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        raw.flush();
        socket.setSoTimeout(0);
        DataOutputStream out = new DataOutputStream(raw);
        AudioPeer peer = new AudioPeer(socket);
        audioClients.add(peer);
        peer.start();
        // Keep a reader alive to notice browser closes and remove stale audio clients promptly.
        try {
            while (running && socket.getInputStream().read() != -1) { /* browsers send no application data */ }
        } finally {
            peer.close();
        }
    }

    private void mjpeg(Socket socket) throws Exception {
        OutputStream out = socket.getOutputStream();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=frame\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
        byte[] previous = null;
        while (running) {
            byte[] frame = cameraActive ? latestJpeg : null;
            if (frame != null && frame != previous) {
                out.write(("--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + frame.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(frame); out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                previous = frame;
            }
            Thread.sleep(10);
        }
        socket.close();
    }

    private void writeResponse(OutputStream out, int status, String type, String body) throws IOException { writeResponse(out, status, type, body.getBytes(StandardCharsets.UTF_8)); }
    private void writeResponse(OutputStream out, int status, String type, byte[] body) throws IOException {
        String phrase = status == 200 ? "OK" : "Not Found";
        out.write(("HTTP/1.1 " + status + " " + phrase + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length + "\r\nCache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(body); out.flush();
    }

    private byte[] readAll(InputStream in) throws IOException { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); return out.toByteArray(); }

    synchronized void stop() {
        running = false;
        try { if (listener != null) listener.close(); } catch (IOException ignored) {}
        listener = null;
        synchronized (audioClients) { for (AudioPeer peer : new ArrayList<>(audioClients)) peer.close(); audioClients.clear(); }
        cameraActive = false; latestJpeg = null;
    }
}


