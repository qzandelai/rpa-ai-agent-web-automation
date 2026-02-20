package com.rpaai.service;

import com.rpaai.entity.BrowserSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BrowserSessionManager {

    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, List<String>> userSessions = new ConcurrentHashMap<>();

    public void registerSession(BrowserSession session) {
        sessions.put(session.getWebsocketSessionId(), session);
        userSessions.computeIfAbsent(session.getUserId(), k -> new ArrayList<>())
                .add(session.getWebsocketSessionId());
    }

    public void unregisterSession(String sessionId) {
        BrowserSession session = sessions.remove(sessionId);
        if (session != null) {
            List<String> userSess = userSessions.get(session.getUserId());
            if (userSess != null) {
                userSess.remove(sessionId);
            }
        }
    }

    public Optional<BrowserSession> getAvailableSession(String userId) {
        List<String> sessIds = userSessions.getOrDefault(userId, Collections.emptyList());

        return sessIds.stream()
                .map(sessions::get)
                .filter(Objects::nonNull)
                .filter(s -> "ACTIVE".equals(s.getStatus()))
                .max(Comparator.comparingLong(BrowserSession::getLastHeartbeat));
    }

    public Optional<BrowserSession> getSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void updatePageInfo(String sessionId, String url, String title) {
        BrowserSession session = sessions.get(sessionId);
        if (session != null) {
            session.setCurrentUrl(url);
            session.setCurrentTitle(title);
            session.setLastActivityTime(System.currentTimeMillis());
        }
    }

    public void updateHeartbeat(String sessionId) {
        BrowserSession session = sessions.get(sessionId);
        if (session != null) {
            session.setLastHeartbeat(System.currentTimeMillis());
        }
    }

    public Collection<BrowserSession> getAllSessions() {
        return Collections.unmodifiableCollection(sessions.values());
    }
}