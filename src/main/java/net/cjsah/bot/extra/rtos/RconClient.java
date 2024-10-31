package net.cjsah.bot.extra.rtos;

import cn.hutool.core.util.IdUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RconClient {
    private static final Logger log = LoggerFactory.getLogger("RconClient");
    private final Map<Integer, Response> locks;
    private Thread thread;
    private Socket client;

    public RconClient() {
        this.locks = new LinkedHashMap<>();
        this.thread = new Thread(() -> {
            while (!this.thread.isInterrupted()) {
                try {
                    if (locks.isEmpty()) {
                        TimeUnit.MICROSECONDS.sleep(100);
                        continue;
                    }
                    this.parsePackage();
                } catch (InterruptedException | IOException ignored) {}
            }
        });
        this.thread.start();
    }

    public void close() throws IOException {
        if (this.client != null && (this.client.isConnected() || !this.client.isClosed())) {
            this.client.close();
        }
    }

    public void shutdown() throws IOException {
        this.close();
        if (this.thread == null) return;
        this.thread.interrupt();
    }

    private void makeSocket() throws SocketException {
        this.client = new Socket();
        this.client.setKeepAlive(true);
        this.client.setTrafficClass(0x04);
        this.client.setSendBufferSize(1460);
        this.client.setReceiveBufferSize(4096);
    }

    public void connect(String hostname, int port, String password) throws IOException, InterruptedException {
        if (this.client != null) {
            if (this.client.isConnected()) {
                throw new IllegalStateException("Already connected");
            } else if (!this.client.isClosed()) {
                this.client.close();
            }
        }
        this.makeSocket();
        this.client.connect(new InetSocketAddress(hostname, port));
        this.login(password);
    }

    private void login(String password) throws IOException, InterruptedException {
        byte[] bytes = password.getBytes(StandardCharsets.US_ASCII);
        this.request(3, bytes, StandardCharsets.US_ASCII);
    }

    public String command(String payload) throws IOException, InterruptedException {
        return this.request(2, payload.getBytes());
    }

    public String request(int type, byte[] payload) throws IOException, InterruptedException {
        return this.request(type, payload, StandardCharsets.UTF_8);
    }

    public String request(int type, byte[] payload, Charset charset) throws IOException, InterruptedException {
        int length = payload.length + 10;
        int id = IdUtil.fastSimpleUUID().hashCode();
        byte[] out = new byte[length + 4];
        ByteBuffer buf = ByteBuffer.wrap(out);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(length).putInt(id).putInt(type).put(payload).putShort((short) 0);
        this.client.getOutputStream().write(out);
        Response res = new Response(charset);
        synchronized (res.lock) {
            this.locks.put(id, res);
            res.lock.wait();
        }
        return res.response;
    }

    private void parsePackage() throws IOException {
        byte[] in = new byte[4096];
        ByteBuffer buf = ByteBuffer.wrap(in);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int bytesRead = 0;
        while (bytesRead < 12) {
            buf.position(bytesRead);
            bytesRead += client.getInputStream().read(in, bytesRead, buf.remaining());
        }
        buf.rewind();

        int length = buf.getInt();
        int id = buf.getInt();
        int type = buf.getInt();
        // Length includes requestId, type, and the two null bytes.
        // Subtract 10 to ignore those values from the payload size.
        byte[] payload = new byte[length - 10];

        buf.mark();
        while (bytesRead - 12 < payload.length + 2) {
            buf.position(bytesRead);
            bytesRead += client.getInputStream().read(in, bytesRead, buf.remaining());
        }
        buf.reset();
        buf.get(payload);
        Response res = this.locks.get(id);
        if (res == null) return;
        synchronized (res.lock) {
            res.response = new String(payload, res.charset);
            res.lock.notifyAll();
        }
        this.locks.remove(id);
    }

    private static class Response {
        private final Object lock = new Object();
        private final Charset charset;
        private String response;

        public Response(Charset charset) {
            this.charset = charset;
        }
    }
}
