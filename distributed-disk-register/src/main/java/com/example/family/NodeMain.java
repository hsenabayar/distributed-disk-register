package com.example.family;

import com.example.command.Command;
import com.example.command.CommandParser; 
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import com.example.store.MessageStore;

public class NodeMain {
    private static final int START_PORT = 5555;
    private static final int CLIENT_COMMAND_PORT = 6666;
    private static final int PRINT_INTERVAL_SECONDS = 10;
    
    public static final NodeRegistry registry = new NodeRegistry();
    public static final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Integer, List<NodeInfo>> messageLocations = new ConcurrentHashMap<>();
    
    private static final ExecutorService clientPool = Executors.newFixedThreadPool(10); 
    private static int TOLERANCE = 1;
    private static MessageStore messageStore;

    public static void main(String[] args) throws Exception {
        TOLERANCE = readToleranceConfig();
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);
        messageStore = new MessageStore("data_" + port);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .setId("Node_" + port)
                .build();

        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);

        Server server = ServerBuilder.forPort(port)
                .addService(service)
                .build()
                .start();

        System.out.printf("Node started on %s:%d (Tolerance: %d)%n", host, port, TOLERANCE);

        if (port != START_PORT) {
            discoverExistingNodes(host, port, registry, self);
        } else {
            startLeaderCommandListener(); 
            startHealthChecker(registry, self);
        }

        startFamilyPrinter(registry, self);
        server.awaitTermination();
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            for (NodeInfo n : members) {
                if (n.getPort() == self.getPort()) continue;

                ManagedChannel channel = null;
                try {
                    channel = ManagedChannelBuilder.forAddress(n.getHost(), n.getPort())
                            .usePlaintext()
                            .build();
                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);
                    
                
                    stub.withDeadlineAfter(2, TimeUnit.SECONDS).getFamilyView(family.Empty.getDefaultInstance());
                    
                } catch (Exception e) {
                    System.out.println("⚠️ Üye çevrimdışı: " + n.getPort());
                    registry.remove(n); 
                } finally {
                    if (channel != null) {
                        channel.shutdown();
                    }
                }
            }
        }, 5, 5, TimeUnit.SECONDS); 
    }

    private static int readToleranceConfig() {
        try {
            Path path = Paths.get("tolerance.conf");
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path);
                for (String line : lines) {
                    if (line.trim().startsWith("TOLERANCE=")) return Integer.parseInt(line.split("=")[1].trim());
                }
            }
        } catch (Exception e) { }
        return 1;
    }

    public static int getTolerance() { return TOLERANCE; }

    private static void discoverExistingNodes(String host, int selfPort, NodeRegistry registry, NodeInfo self) {
        try {
            ManagedChannel channel = ManagedChannelBuilder.forAddress(host, START_PORT).usePlaintext().build();
            FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);
            FamilyView view = stub.join(self);
            for (NodeInfo member : view.getMembersList()) { registry.add(member); }
            System.out.println("✅ Lidere kayıt olundu. Üye sayısı: " + view.getMembersCount());
            channel.shutdown();
        } catch (Exception e) { System.err.println("❌ Lider hatası: " + e.getMessage()); }
    }

    private static void startLeaderCommandListener() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(CLIENT_COMMAND_PORT)) {
                System.out.println("✅ [LEADER] TCP 6666 Dinleniyor...");
                while (true) {
                    Socket client = serverSocket.accept();
                    clientPool.execute(() -> handleClientCommandConnection(client));
                }
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    private static void handleClientCommandConnection(Socket client) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                Command command = CommandParser.parse(line.trim());
                if (command != null) out.println(command.execute(storage, messageStore));
                else out.println("ERROR");
            }
        } catch (Exception ignored) { }
    }

    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) { return port; }
            catch (IOException e) { port++; }
        }
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self) {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            boolean isLeader = self.getPort() == START_PORT;

            // Klasördeki dosya sayısını güvenli şekilde say
            long messageCount = 0;
            try {
                Path path = Paths.get("data_" + self.getPort());
                if (Files.exists(path)) {
                    // Sadece dosya olanları say (klasörleri hariç tut)
                    messageCount = Files.list(path)
                                        .filter(Files::isRegularFile)
                                        .count();
                }
            } catch (IOException e) { 
                // Klasör henüz oluşmadıysa hata basmasın diye boş bırakılabilir
            }

            System.out.println("\n========= SYSTEM STATUS =========");
            System.out.printf("Node: %d (%s)%n", self.getPort(), isLeader ? "LEADER" : "MEMBER");
            System.out.printf("Active Members in Cluster: %d%n", members.size());
            System.out.printf("Local Storage Usage: %d files%n", messageCount); // ÖDEVİN İSTEDİĞİ KRİTİK SATIR
            System.out.println("Time: " + LocalDateTime.now().withNano(0));
            System.out.println("=================================");
            
        }, 5, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
}