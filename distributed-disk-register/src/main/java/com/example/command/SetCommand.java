package com.example.command;

import com.example.family.NodeMain;
import com.example.store.MessageStore;
import family.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SetCommand extends Command {
    private final String messageId;
    private final String message;
    
    // Round-Robin için statik sayaç (Liderin her SET'te bir sonrakine geçmesini sağlar)
    private static final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public SetCommand(String messageId, String message) {
        this.messageId = messageId;
        this.message = message;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage, MessageStore messageStore) {
        int idInt = Integer.parseInt(messageId);
        
        // 1. Kendi diskine kaydet
        if (!messageStore.writeMessage(messageId, message)) {
            return "ERROR: LOCAL_DISK_FAILED";
        }

        // 2. Diğer üyeleri listele (Lider hariç)
        List<NodeInfo> allMembers = NodeMain.registry.snapshot();
        List<NodeInfo> availableMembers = new ArrayList<>();
        for (NodeInfo m : allMembers) {
            if (m.getPort() != 5555) { 
                availableMembers.add(m);
            }
        }

        int tolerance = NodeMain.getTolerance();
        int successCount = 0;
        List<NodeInfo> savedNodes = new ArrayList<>();

        // 3. LOAD BALANCING (Round-Robin Seçimi)
        if (!availableMembers.isEmpty()) {
            int membersToSelect = Math.min(tolerance, availableMembers.size());
            
            for (int i = 0; i < membersToSelect; i++) {
                // Round-Robin indeksi al ve bir artır
                int index = roundRobinCounter.getAndIncrement() % availableMembers.size();
                NodeInfo target = availableMembers.get(index);
                
                try {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(target.getHost(), target.getPort())
                            .usePlaintext().build();
                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                    StoreResponse resp = stub.store(StoredMessage.newBuilder()
                            .setMessageId(idInt)
                            .setContent(message)
                            .build());

                    if (resp.getSuccess()) {
                        successCount++;
                        savedNodes.add(target);
                    }
                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
                } catch (Exception e) {
                    System.err.println("❌ Üye " + target.getPort() + " hatası: " + e.getMessage());
                }
            }
        }

        // 4. Tolerans kontrolü ve Yanıt

        if (successCount >= tolerance || availableMembers.isEmpty()) {
            NodeMain.messageLocations.put(idInt, savedNodes);
            storage.put(messageId, "OK");
            return "OK";
        }

        return "ERROR: REPLICATION_FAILED (Success: " + successCount + "/" + tolerance + ")";
    }
}