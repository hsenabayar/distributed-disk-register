package com.example.family;

import family.*;
import com.example.store.MessageStore;
import io.grpc.stub.StreamObserver;
import java.io.IOException;

public class FamilyServiceImpl extends FamilyServiceGrpc.FamilyServiceImplBase {

    private final NodeRegistry registry;
    private final NodeInfo self;
    private final MessageStore messageStore;

    public FamilyServiceImpl(NodeRegistry registry, NodeInfo self) {
        this.registry = registry;
        this.self = self;
        this.registry.add(self);
        this.messageStore = new MessageStore("data_" + self.getPort());
    }

    // SAĞLIK KONTROLÜ YANITI (YENİ EKLENDİ)
    @Override
    public void getFamilyView(family.Empty request, StreamObserver<FamilyView> responseObserver) {
        FamilyView view = FamilyView.newBuilder()
                .addAllMembers(registry.snapshot())
                .build();
        responseObserver.onNext(view);
        responseObserver.onCompleted();
    }

    @Override
    public void store(StoredMessage request, StreamObserver<StoreResponse> responseObserver) {
        try {
            messageStore.put(request.getMessageId(), request.getContent());
            StoreResponse response = StoreResponse.newBuilder().setSuccess(true).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (IOException e) { responseObserver.onError(e); }
    }

    @Override
    public void retrieve(GetRequest request, StreamObserver<StoredMessage> responseObserver) {
        try {
            String content = messageStore.get(request.getMessageId());
            StoredMessage message = StoredMessage.newBuilder()
                    .setMessageId(request.getMessageId())
                    .setContent(content != null ? content : "")
                    .build();
            responseObserver.onNext(message);
            responseObserver.onCompleted();
        } catch (IOException e) { responseObserver.onError(e); }
    }

    @Override
    public void join(NodeInfo request, StreamObserver<FamilyView> responseObserver) {
        registry.add(request);
        System.out.println("➕ Üye katıldı: " + request.getPort());
        FamilyView view = FamilyView.newBuilder().addAllMembers(registry.snapshot()).build();
        responseObserver.onNext(view);
        responseObserver.onCompleted();
    }
}