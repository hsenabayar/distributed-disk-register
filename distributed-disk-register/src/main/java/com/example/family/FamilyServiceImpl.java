package com.example.family;

import family.FamilyServiceGrpc;
import family.StoredMessage;
import family.StoreResponse;
import family.GetRequest;
import family.NodeInfo;
import family.FamilyView;
import com.example.store.MessageStore;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private final MessageStore messageStore; // Diske kayıt için

    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self) {
        this.registry = registry;
        this.self = self;
        this.registry.add(self);
        // Her üye kendi klasöründe (port ismiyle ayrılmış) mesajları saklar
        this.messageStore = new MessageStore("data_" + self.getPort());
    }

    // 3. Aşama Görevi: Liderden gelen mesajı diske kaydetme
    @Override
    public void store(StoredMessage request, StreamObserver<StoreResponse> responseObserver) {
        try {
            // Protobuf nesnesinden gelen veriyi diske yazıyoruz
            messageStore.put(request.getMessageId(), request.getContent());
            
            System.out.println("✅ Mesaj diske kaydedildi: ID=" + request.getMessageId());

            StoreResponse response = StoreResponse.newBuilder()
                    .setSuccess(true)
                    .setInfo("Mesaj " + self.getId() + " tarafından başarıyla kaydedildi.")
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IOException e) {
            responseObserver.onError(e);
        }
    }

    // 3. Aşama Görevi: ID'ye göre diske kayıtlı mesajı getirme
    @Override
    public void retrieve(GetRequest request, StreamObserver<StoredMessage> responseObserver) {
        try {
            String content = messageStore.get(request.getMessageId());
            
            if (content != null) {
                StoredMessage message = StoredMessage.newBuilder()
                        .setMessageId(request.getMessageId())
                        .setContent(content)
                        .build();
                responseObserver.onNext(message);
            } else {
                // Mesaj bulunamadıysa boş dönebilir veya hata fırlatabiliriz
                responseObserver.onNext(StoredMessage.newBuilder().build());
            }
            responseObserver.onCompleted();
        } catch (IOException e) {
            responseObserver.onError(e);
        }
    }

    // Aileye yeni üye ekleme (Mevcut yapınızdan korunmuştur)
    @Override
    public void join(NodeInfo request, StreamObserver<FamilyView> responseObserver) {
        registry.add(request);

        // addAllMembers yerine addMembers kullanın (field adı members ise)
        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot()) 
                .build();

        responseObserver.onNext(view);
        responseObserver.onCompleted();
    }
}