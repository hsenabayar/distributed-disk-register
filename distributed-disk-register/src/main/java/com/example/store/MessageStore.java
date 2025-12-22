package com.example.store;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MessageStore {
    private String storageDir = "data"; // Varsayılan dizin

    // NodeMain'deki parametresiz kullanım için
    public MessageStore() {
        init();
    }

    // FamilyServiceImpl'deki parametreli kullanım için (data_5555 vb.)
    public MessageStore(String storageDir) {
        this.storageDir = storageDir;
        init();
    }

    private void init() {
        try {
            Files.createDirectories(Paths.get(storageDir));
        } catch (IOException e) {
            System.err.println("Dizin oluşturulamadı: " + e.getMessage());
        }
    }

    // SetCommand için (String ID kullanır)
    public boolean writeMessage(String messageId, String content) {
        try {
            put(Integer.parseInt(messageId), content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // GetCommand için (String ID kullanır)
    public String readMessage(String messageId) {
        try {
            return get(Integer.parseInt(messageId));
        } catch (Exception e) {
            return null;
        }
    }

    // FamilyServiceImpl için (int ID kullanır)
    public void put(int messageId, String content) throws IOException {
        Path path = Paths.get(storageDir, messageId + ".txt");
        Files.writeString(path, content);
    }

    // FamilyServiceImpl için (int ID kullanır)
    public String get(int messageId) throws IOException {
        Path path = Paths.get(storageDir, messageId + ".txt");
        if (Files.exists(path)) {
            return Files.readString(path);
        }
        return null;
    }
}