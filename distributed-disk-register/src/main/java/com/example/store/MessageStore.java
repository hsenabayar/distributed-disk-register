package com.example.store;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MessageStore {
    // Mesajların saklanacağı ana dizin (Projenin çalıştığı dizinde oluşur)
    private static final String BASE_DIR = "messages";
    
    public MessageStore() {
        // Uygulama başladığında messages klasörünü oluştur.
        try {
            Files.createDirectories(Paths.get(BASE_DIR));
            System.out.println("📁 [DISK] Mesaj depolama dizini olusturuldu: " + BASE_DIR);
        } catch (IOException e) {
            System.err.println("❌ [DISK] Mesaj dizini olusturulamadi: " + e.getMessage());
        }
    }

    /**
     * Mesajı diske kaydeder (messages/<id>.msg dosyasına).
     */
    public boolean writeMessage(String id, String message) {
        Path filePath = Paths.get(BASE_DIR, id + ".msg");
        
        try (BufferedWriter writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8)) {
            writer.write(message);
            writer.flush();
            return true;
        } catch (IOException e) {
            System.err.printf("❌ [DISK] %s kaydetme hatasi: %s\n", filePath, e.getMessage());
            return false;
        }
    }

    /**
     * ID'ye karşılık gelen mesaj dosyasını diskten okur.
     */
    public String readMessage(String id) {
        Path filePath = Paths.get(BASE_DIR, id + ".msg");
        
        if (!Files.exists(filePath)) {
            return null; 
        }

        try {
            // Files.readString Buffered I/O kullanır.
            return Files.readString(filePath, StandardCharsets.UTF_8); 
        } catch (IOException e) {
            System.err.printf("❌ [DISK] %s okuma hatasi: %s\n", filePath, e.getMessage());
            return null;
        }
    }
}