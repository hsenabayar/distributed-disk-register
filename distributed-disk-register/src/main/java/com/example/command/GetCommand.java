package com.example.command;

import com.example.family.NodeMain;
import com.example.store.MessageStore;
import family.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class GetCommand extends Command {
    private final String messageId;

    public GetCommand(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage, MessageStore messageStore) {
        // 1. Liderin kendi diskine bak (En hızlı ve güvenli yol)
        String local = messageStore.readMessage(messageId);
        if (local != null) return local;

        // 2. Kendi diskinde yoksa, kayıtlı olduğu üyelere bak
        int idInt;
        try {
            idInt = Integer.parseInt(messageId);
        } catch (NumberFormatException e) {
            return "ERROR: INVALID_ID";
        }

        List<NodeInfo> locations = NodeMain.messageLocations.get(idInt);

        if (locations != null && !locations.isEmpty()) {
            // RECOVERY: Listedeki üyeleri sırayla dene (Failover)
            for (NodeInfo target : locations) {
                ManagedChannel channel = null;
                try {
                    channel = ManagedChannelBuilder.forAddress(target.getHost(), target.getPort())
                            .usePlaintext()
                            .build();

                    // Üye çökmüşse sonsuza kadar bekleme, 2 saniye sonra vazgeç ve diğerine geç
                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc
                            .newBlockingStub(channel)
                            .withDeadlineAfter(2, TimeUnit.SECONDS);

                    StoredMessage response = stub.retrieve(GetRequest.newBuilder()
                            .setMessageId(idInt)
                            .build());

                    // İçerik boş değilse başarılıdır, döndür
                    if (response != null && !response.getContent().isEmpty()) {
                        return response.getContent();
                    }
                } catch (Exception e) {
                    // CRASH DURUMU: Burası 7. Aşamanın kalbi. Hata alınca döngü devam eder, sıradaki üyeye sorar.
                    System.err.println("⚠️ Üye " + target.getPort() + " çökmüş veya cevap vermiyor, sıradaki yedeğe bakılıyor...");
                } finally {
                    if (channel != null) {
                        channel.shutdownNow();
                    }
                }
            }
        }

        return "NOT_FOUND";
    }
}