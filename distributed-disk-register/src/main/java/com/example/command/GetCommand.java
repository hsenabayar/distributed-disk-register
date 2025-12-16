package com.example.command;

import com.example.store.MessageStore; 
import java.util.concurrent.ConcurrentHashMap;

public class GetCommand extends Command {
    private final String messageId;

    public GetCommand(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage, MessageStore messageStore) {
        // Mesajı diskten okuma işlemini MessageStore'a devret.
        String result = messageStore.readMessage(messageId);

        if (result != null) {
            System.out.printf("[LEADER] Mesaj diskten bulundu. ID: %s\n", messageId);
            return result; // İstemciye mesajın kendisini döndür.
        } else {
            // Aşama 3'te buraya Hata Toleranslı okuma mantığı eklenecek.
            System.out.printf("[LEADER] Mesaj diskte bulunamadı. ID: %s\n", messageId);
            return "NOT_FOUND";
        }
    }
}