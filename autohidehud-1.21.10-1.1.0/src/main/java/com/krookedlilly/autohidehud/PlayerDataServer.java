package com.krookedlilly.autohidehud;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerDataServer {
    public static int PORT = 25922;

    private ServerSocket serverSocket;
    private Thread serverThread;
    private volatile boolean running = false;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final Queue<String> dataQueue = new ConcurrentLinkedQueue<>();

    public boolean isRunning() {
        return running;
    }

    public void start() {
        if (running) return;

        running = true;
        serverThread = new Thread(this::run, "PlayerDataServer");
        serverThread.setDaemon(true);
        serverThread.start();
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(PORT);
            AutoHideHUD.LOGGER.info("Player data server started on port " + PORT);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    AutoHideHUD.LOGGER.info("Client connected: " + clientSocket.getInetAddress());

                    ClientHandler handler = new ClientHandler(clientSocket);
                    clients.add(handler);

                } catch (IOException e) {
                    if (running) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeServer();
        }
    }

    public void stop() {
        if (!running) return;

        running = false;

        // Close server socket first to stop accepting new connections
        closeServer();

        // Schedule cleanup on a separate thread to avoid blocking
        Thread cleanupThread = new Thread(() -> {
            for (ClientHandler client : clients) {
                client.close();
            }
            clients.clear();

            if (serverThread != null) {
                try {
                    serverThread.join(1000); // Wait max 1 second
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            AutoHideHUD.LOGGER.info("Player data server cleanup complete");
        }, "PlayerDataServer-Cleanup");

        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private void closeServer() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void broadcastPlayerData(String jsonData) {
        dataQueue.offer(jsonData);
        sendQueuedData();
    }

    private void sendQueuedData() {
        String data;
        while ((data = dataQueue.poll()) != null) {
            for (ClientHandler client : clients) {
                client.send(data);
            }
        }
    }

    private class ClientHandler {
        private final Socket socket;
        private PrintWriter out;
        private volatile boolean connected = true;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                this.out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                e.printStackTrace();
                connected = false;
            }
        }

        public void send(String data) {
            if (connected && out != null) {
                try {
                    out.println(data);
                    if (out.checkError()) {
                        close();
                    }
                } catch (Exception e) {
                    close();
                }
            }
        }

        public void close() {
            if (!connected) return;

            connected = false;
            clients.remove(this);

            try {
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                AutoHideHUD.LOGGER.info("Client disconnected");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}