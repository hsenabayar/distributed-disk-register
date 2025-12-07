package com.example.command;

import java.util.concurrent.ConcurrentHashMap;

public class GetCommand extends Command {
    private final String messageId;

    public GetCommand(String messageId) {
        this.messageId = messageId;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage) {
        // Ödevde istenen: GET -> map.get(id) + bulunamazsa NOT_FOUND
        
        String result = storage.get(messageId);

        if (result != null) {
            System.out.printf("[LEADER] Mesaj bulundu. ID: %s\n", messageId);
            // Istemciye mesajın kendisini döndür.
            return result; 
        } else {
            System.out.printf("[LEADER] Mesaj bulunamadı. ID: %s\n", messageId);
            // Mesaj bulunamazsa NOT_FOUND döndür.
            return "NOT_FOUND";
        }
    }
}