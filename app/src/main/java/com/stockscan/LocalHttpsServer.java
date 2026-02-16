package com.stockscan;

import android.content.res.AssetManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class LocalHttpsServer {

    private final int port;
    private final AssetManager assets;
    private ServerSocket serverSocket;
    private ExecutorService executor;
    private volatile boolean running = false;

    public LocalHttpsServer(int port, AssetManager assets) {
        this.port = port;
        this.assets = assets;
    }

    public void start() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Generate RSA key pair
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.generateKeyPair();

        // Self-sign a certificate for localhost
        X500Name name = new X500Name("CN=localhost");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date from = new Date();
        Date to = new Date(from.getTime() + 10L * 365 * 24 * 60 * 60 * 1000);

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA")
                .build(keyPair.getPrivate());
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, serial, from, to, name, keyPair.getPublic());
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(builder.build(signer));

        // Build KeyStore
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("key", keyPair.getPrivate(), new char[0],
                new java.security.cert.Certificate[]{cert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, new char[0]);

        SSLContext sslCtx = SSLContext.getInstance("TLS");
        sslCtx.init(kmf.getKeyManagers(), null, new SecureRandom());

        SSLServerSocketFactory ssf = sslCtx.getServerSocketFactory();
        serverSocket = ssf.createServerSocket(port);

        running = true;
        executor = Executors.newCachedThreadPool();

        // Accept loop in background thread
        executor.submit(() -> {
            while (running) {
                try {
                    Socket socket = serverSocket.accept();
                    executor.submit(() -> handleRequest(socket));
                } catch (Exception e) {
                    if (running) e.printStackTrace();
                }
            }
        });
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        if (executor != null) executor.shutdownNow();
    }

    private void handleRequest(Socket socket) {
        try (socket) {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();

            // Read request line
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            // Parse path: "GET /index.html HTTP/1.1"
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String path = parts[1];
            if (path.equals("/") || path.isEmpty()) path = "/index.html";

            // Consume headers
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) { /* skip */ }

            // Strip leading slash to get asset name
            String assetName = path.startsWith("/") ? path.substring(1) : path;

            try (InputStream assetStream = assets.open(assetName)) {
                byte[] data = readAll(assetStream);
                String mime = getMime(assetName);

                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.1 200 OK\r\n");
                pw.print("Content-Type: " + mime + "\r\n");
                pw.print("Content-Length: " + data.length + "\r\n");
                pw.print("Cache-Control: no-cache\r\n");
                pw.print("Connection: close\r\n");
                pw.print("\r\n");
                pw.flush();
                out.write(data);
                out.flush();
            } catch (IOException e) {
                // 404
                String body = "Not found: " + assetName;
                PrintWriter pw = new PrintWriter(out);
                pw.print("HTTP/1.1 404 Not Found\r\n");
                pw.print("Content-Type: text/plain\r\n");
                pw.print("Content-Length: " + body.length() + "\r\n");
                pw.print("\r\n");
                pw.print(body);
                pw.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = in.read(chunk)) != -1) buf.write(chunk, 0, n);
        return buf.toByteArray();
    }

    private static String getMime(String name) {
        if (name.endsWith(".html") || name.endsWith(".htm")) return "text/html; charset=utf-8";
        if (name.endsWith(".js"))  return "application/javascript";
        if (name.endsWith(".css")) return "text/css";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
