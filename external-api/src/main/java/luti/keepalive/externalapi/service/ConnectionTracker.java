package luti.keepalive.externalapi.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectionTracker {

    private final ConcurrentHashMap<Integer, Boolean> seenPorts = new ConcurrentHashMap<>();

    public boolean isNewConnection(int remotePort) {
        return seenPorts.putIfAbsent(remotePort, Boolean.TRUE) == null;
    }

    public void reset() {
        seenPorts.clear();
    }
}
