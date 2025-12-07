package com.example.command;

import java.util.concurrent.ConcurrentHashMap;

public class SetCommand extends Command {
    private final String messageId;
    private final String message;

    public SetCommand(String messageId, String message) {
        this.messageId = messageId;
        this.message = message;
    }

    @Override
    public String execute(ConcurrentHashMap<String, String> storage) {
        // Ödevde istenen: SET -> map.put(id, msg) + OK
        
        // Bu aşamada sadece liderin kendi hafızasına kaydını yapıyoruz.
        // Hata toleransı (gRPC ile diğer üyelere gönderme) sonraki aşamalarda eklenecektir.
        storage.put(messageId, message);
        
        System.out.printf("[LEADER] Mesaj Kaydedildi. ID: %s, Mesaj: %s\n", messageId, message);
        return "OK";
    }
}