package org.barrelmc.barrel.auth;

import lombok.Getter;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private static AuthManager instance;
    @Getter
    private final Map<String, BedrockAuthManager> xboxAccounts = new ConcurrentHashMap<>();
    @Getter
    private final Map<String, Thread> loginThreads = new ConcurrentHashMap<>();
    @Getter
    private final Live xboxLive;

    public AuthManager() {
        xboxLive = new Live();
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }
}
