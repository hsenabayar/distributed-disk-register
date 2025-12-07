package com.example.command;

/**
 * Istemci metin girdilerini Command nesnelerine ayrıştırmaktan sorumludur.
 */
public class CommandParser {

    /**
     * Istemciden gelen metin satırını ayrıştırır ve uygun Command nesnesini döndürür.
     * @param line Istemciden gelen metin (orn: "SET 10 Hello World" veya "GET 10")
     * @return Command nesnesi (SetCommand veya GetCommand) veya hata durumunda null.
     */
    public static Command parse(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // Metni en fazla 3 parçaya böl: 1.Komut Tipi, 2.ID, 3.Mesaj (Geriye kalan her şey)
        String[] parts = line.trim().split("\\s+", 3); 
        String commandType = parts[0].toUpperCase();

        if (commandType.equals("SET")) {
            if (parts.length < 3) {
                System.err.println("ERROR: SET komutu eksik parametre iceriyor. Dogru kullanim: SET <id> <message>");
                return null;
            }
            String messageId = parts[1];
            String message = parts[2];
            return new SetCommand(messageId, message);
        } 
        else if (commandType.equals("GET")) {
            if (parts.length != 2) {
                System.err.println("ERROR: GET komutu yanlis parametre sayisi iceriyor. Dogru kullanim: GET <id>");
                return null;
            }
            String messageId = parts[1];
            return new GetCommand(messageId);
        }
        else {
            System.err.println("ERROR: Bilinmeyen komut turu: " + commandType);
            return null;
        }
    }
}