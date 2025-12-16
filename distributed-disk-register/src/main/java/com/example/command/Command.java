package com.example.command;
import com.example.store.MessageStore;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tüm istemci komutları (SET, GET) için temel soyut sınıf.
 */
public abstract class Command {
    
    /**
     * Komutu yürütür ve istemciye dönecek yanıtı String olarak döndürür.
     * @param storage Lider sunucunun mesajları tuttuğu ConcurrentHashMap<ID, Mesaj>.
     * @return Istemciye gonderilecek yanit (OK, NOT_FOUND veya Mesajin kendisi).
     */
    public abstract String execute(ConcurrentHashMap<String, String> storage, MessageStore messageStore);
}