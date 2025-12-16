package com.example.command;

import com.example.store.MessageStore;
import java.util.concurrent.ConcurrentHashMap;

public class SetCommand extends Command {
    private final String messageId;
    private final String message;

    public SetCommand(String messageId, String message) {
        this.messageId = messageId;
        this.message = message;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage, MessageStore messageStore) {
        // Mesajı diske kaydetme işlemini MessageStore'a devret.
        if (messageStore.writeMessage(messageId, message)) {
            System.out.printf("[LEADER] Mesaj Diske Kaydedildi. ID: %s\n", messageId);
            
            // Liderin Map'ini, sadece diskte hangi mesajların olduğunu takip etmek için kullanabiliriz.
            storage.put(messageId, ""); 
            
            return "OK";
        } else {
            return "ERROR: DISK_WRITE_FAILED";
        }
    }
}