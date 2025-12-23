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
        // 1. Liderin kendi diskine bak
        String local = messageStore.readMessage(messageId);
        if (local != null) return local;

        // 2. Kendi diskinde yoksa üyelere bak
        int idInt = Integer.parseInt(messageId);
        List<NodeInfo> locations = NodeMain.messageLocations.get(idInt);

        if (locations != null) {
            for (NodeInfo target : locations) {
                try {
                    ManagedChannel channel = ManagedChannelBuilder.forAddress(target.getHost(), target.getPort())
                            .usePlaintext().build();
                    FamilyServiceGrpc.FamilyServiceBlockingStub stub = FamilyServiceGrpc.newBlockingStub(channel);

                    StoredMessage response = stub.retrieve(GetRequest.newBuilder()
                            .setMessageId(idInt)
                            .build());

                    channel.shutdown().awaitTermination(1, TimeUnit.SECONDS);

                    if (response != null && !response.getContent().isEmpty()) {
                        return response.getContent();
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Üye cevap vermedi, sıradaki deneniyor...");
                }
            }
        }

        return "NOT_FOUND";
    }
}