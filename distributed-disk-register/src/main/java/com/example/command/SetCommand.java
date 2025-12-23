package com.example.command;

import com.example.family.NodeMain;
import com.example.store.MessageStore;
import family.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class SetCommand extends Command {
    private final String messageId;
    private final String message;

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

        // 2. Diğer üyeleri listele
        List<NodeInfo> allMembers = NodeMain.registry.snapshot();
        List<NodeInfo> availableMembers = new ArrayList<>();
        for (NodeInfo m : allMembers) {
            if (m.getPort() != 5555) { // Lider hariç üyeleri al
                availableMembers.add(m);
            }
        }

        int tolerance = NodeMain.getTolerance();
        int successCount = 0;
        List<NodeInfo> savedNodes = new ArrayList<>();

        // 3. gRPC ile Store RPC'si gönder
        for (int i = 0; i < Math.min(tolerance, availableMembers.size()); i++) {
            NodeInfo target = availableMembers.get(i);
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

        // 4. Tolerans sağlandıysa haritayı güncelle
        if (successCount >= tolerance || availableMembers.isEmpty()) {
            NodeMain.messageLocations.put(idInt, savedNodes);
            storage.put(messageId, "OK");
            return "OK";
        }

        return "ERROR: REPLICATION_FAILED";
    }
}