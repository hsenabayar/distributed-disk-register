package com.example.family;

import family.NodeInfo;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NodeRegistry {

    private final Set<NodeInfo> nodes = ConcurrentHashMap.newKeySet();

    public void add(NodeInfo node) {
    // Aynı adresteki üyeyi önce sil sonra ekle (Güncel tutmak için)
    remove(node);
    nodes.add(node);
}

    public void addAll(Collection<NodeInfo> others) {
        nodes.addAll(others);
    }

    public List<NodeInfo> snapshot() {
        return List.copyOf(nodes);
    }

    // Bu metodu güvenli silme yapacak şekilde güncelledik
    public void remove(NodeInfo node) {
        nodes.removeIf(n -> n.getPort() == node.getPort() && n.getHost().equals(node.getHost()));
    }
}