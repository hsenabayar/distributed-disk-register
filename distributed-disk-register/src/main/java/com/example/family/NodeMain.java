package com.example.family;

import com.example.command.Command;
import com.example.command.CommandParser; 
import family.Empty;
import family.FamilyServiceGrpc;
import family.FamilyView;
import family.NodeInfo;
import family.ChatMessage; 

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;

import java.io.IOException;
import java.net.ServerSocket;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

import com.example.store.MessageStore;

public class NodeMain {

    private static final int START_PORT = 5555;
    private static final int CLIENT_COMMAND_PORT = 6666; // Istemci komutları için port
    private static final int PRINT_INTERVAL_SECONDS = 10;
    
    // Liderin, dağıtık olarak saklanacak mesajları hafıza içi tuttuğu Map
    private static final ConcurrentHashMap<String, String> storage = new ConcurrentHashMap<>();
    
    
    // Istemci bağlantılarını işlemek için sabit boyutlu thread havuzu
    private static final ExecutorService clientPool = Executors.newFixedThreadPool(10); 

    private static final MessageStore messageStore = new MessageStore();

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = findFreePort(START_PORT);

        NodeInfo self = NodeInfo.newBuilder()
                .setHost(host)
                .setPort(port)
                .build();

        NodeRegistry registry = new NodeRegistry();
        FamilyServiceImpl service = new FamilyServiceImpl(registry, self);

        Server server = ServerBuilder
                .forPort(port)
                .addService(service)
                .build()
                .start();

        System.out.printf("Node started on %s:%d%n", host, port);

        // Eğer bu ilk node ise (port 5555), liderdir ve istemci komutlarını dinlemelidir.
        if (port == START_PORT) {
            // Lider olarak istemci komutlarını dinlemeyi başlat
            startLeaderCommandListener(self); 
        }

        discoverExistingNodes(host, port, registry, self);
        startFamilyPrinter(registry, self);
        startHealthChecker(registry, self);

        server.awaitTermination();
    }
    
    private static void startLeaderCommandListener(NodeInfo self) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(CLIENT_COMMAND_PORT)) {
                System.out.printf("✅ [LEADER] Istemci Komut Dinleyici Başlatıldı: TCP %s:%d%n",
                        self.getHost(), CLIENT_COMMAND_PORT);

                while (true) {
                    Socket client = serverSocket.accept();
                    clientPool.execute(() -> handleClientCommandConnection(client)); // Havuzu kullan
                }

            } catch (IOException e) {
                System.err.println("❌ [LEADER] Istemci Komut Dinleyici Hatası: " + e.getMessage());
                // Node düşerse veya port meşgul olursa buraya düşer.
            }
        }, "LeaderCommandListener").start();
    }

    /**
     * Tek bir istemci bağlantısından gelen komutları işler.
     * SET/GET komutlarını ayrıştırıp çalıştırır.
     */
    private static void handleClientCommandConnection(Socket client) {
        String clientAddress = client.getRemoteSocketAddress().toString();
        System.out.println("🔗 [LEADER] Yeni istemci bağlantısı: " + clientAddress);

        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            PrintWriter out = new PrintWriter(client.getOutputStream(), true); // autoFlush: true
        ) {
            String clientLine;
            
            // Istemciden satır satır komutları oku
            while ((clientLine = in.readLine()) != null) {
                String line = clientLine.trim();
                if (line.isEmpty()) continue;
                
                System.out.printf("📥 [LEADER] %s'den gelen komut: %s\n", clientAddress, line);

                Command command = CommandParser.parse(line);
                String response;

                if (command != null) {
                    // Komutu çalıştır ve yanıtı al. storage map'ini execute metoduna iletiyoruz.
                    // (Aşama 1: Sadece liderin kendi Map'ine kaydeder.)
                    response = command.execute(storage, messageStore); 
                } else {
                    response = "ERROR: Invalid Command Format";
                }
                
                // Yanıtı istemciye geri gönder
                out.println(response); 
                System.out.printf("📤 [LEADER] %s'e gonderilen yanit: %s\n", 
                                  clientAddress, response.length() > 50 ? response.substring(0, 50) + "..." : response);
            }

        } catch (SocketException e) {
            // Istemci aniden bağlantıyı keserse
            System.out.println("❌ [LEADER] Istemci bağlantısı aniden kesildi: " + clientAddress);
        } catch (IOException e) {
            System.err.println("❌ [LEADER] TCP client handler error: " + e.getMessage());
        } finally {
            try { 
                client.close(); 
                System.out.println("🚪 [LEADER] Istemci bağlantısı kapatıldı: " + clientAddress);
            } catch (IOException ignored) {}
        }
    }

    private static int findFreePort(int startPort) {
        int port = startPort;
        while (true) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private static void discoverExistingNodes(String host,
                                             int selfPort,
                                             NodeRegistry registry,
                                             NodeInfo self) {
      
    }

    private static void startFamilyPrinter(NodeRegistry registry, NodeInfo self) {
       
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();
            // Lider isek (START_PORT == self.getPort()) ek bilgi basılabilir
            boolean isLeader = self.getPort() == START_PORT;

            System.out.println("======================================");
            System.out.printf("Family at %s:%d (%s)%n", self.getHost(), self.getPort(), isLeader ? "LEADER" : "MEMBER");
            System.out.println("Time: " + LocalDateTime.now());
            
            
            if (isLeader) {
                 System.out.println("Total Messages Stored (LIDER LOCAL): " + storage.size());
               
            }
            
            System.out.println("Members:");

            for (NodeInfo n : members) {
                boolean isMe = n.getHost().equals(self.getHost()) && n.getPort() == self.getPort();
                System.out.printf(" - %s:%d%s%n",
                        n.getHost(),
                        n.getPort(),
                        isMe ? " (me)" : "");
            }
            System.out.println("======================================");
        }, 3, PRINT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private static void startHealthChecker(NodeRegistry registry, NodeInfo self) {
      
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            List<NodeInfo> members = registry.snapshot();

            for (NodeInfo n : members) {
                
            }

        }, 5, 10, TimeUnit.SECONDS); // 5 sn sonra başla, 10 sn'de bir kontrol et
    }
}