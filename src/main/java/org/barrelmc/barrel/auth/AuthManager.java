/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.auth;

import com.google.gson.JsonParser;
import lombok.Getter;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.util.holder.listener.BasicChangeListener;
import org.barrelmc.barrel.Barrel;
import org.barrelmc.barrel.server.ProxyServer;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AuthManager {

    private static final String ACCOUNTS_FOLDER = "accounts";
    // What a java player can be called, a file is only named after one of these
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static AuthManager instance;

    // The xbox accounts the players signed in to, by the name of the java player
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

    private static boolean rememberLogins() {
        return ProxyServer.getInstance().getConfig().isRememberLogins();
    }

    private static Path getAccountFile(String username) {
        return PLAYER_NAME.matcher(username).matches() ? Paths.get(Barrel.DATA_PATH, ACCOUNTS_FOLDER, username + ".json") : null;
    }

    // Whether the player does not have to sign in to xbox before it joins
    public boolean hasXboxAccount(String username) {
        if (this.xboxAccounts.containsKey(username)) {
            return true;
        }

        Path file = rememberLogins() ? getAccountFile(username) : null;
        if (file == null || !Files.exists(file)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            String bedrockVersion = ProxyServer.getInstance().getBedrockPacketCodec().getMinecraftVersion();
            this.addXboxAccount(username, BedrockAuthManager.fromJson(this.xboxLive.getHttpClient(), bedrockVersion, JsonParser.parseReader(reader).getAsJsonObject()));
            return true;
        } catch (Exception e) {
            System.out.println("The saved xbox login of " + username + " can not be read, the player has to sign in again: " + e);
            return false;
        }
    }

    public void addXboxAccount(String username, BedrockAuthManager xboxAccount) {
        this.xboxAccounts.put(username, xboxAccount);
        if (rememberLogins()) {
            // What a login is made of is renewed from time to time
            xboxAccount.getChangeListeners().add((BasicChangeListener) () -> saveXboxAccount(username, xboxAccount));
            saveXboxAccount(username, xboxAccount);
        }
    }

    // The account a player joins the bedrock server with. One that is not remembered is for one time
    public BedrockAuthManager getXboxAccount(String username) {
        return rememberLogins() ? this.xboxAccounts.get(username) : this.xboxAccounts.remove(username);
    }

    // For a login the server or xbox no longer takes, the player signs in again the next time
    public void forgetXboxAccount(String username) {
        this.xboxAccounts.remove(username);
        Path file = getAccountFile(username);
        try {
            if (file != null) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            System.out.println("The saved xbox login of " + username + " can not be deleted: " + e);
        }
    }

    private static synchronized void saveXboxAccount(String username, BedrockAuthManager xboxAccount) {
        Path file = getAccountFile(username);
        if (file == null) {
            return;
        }

        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                // Whoever can read the file can play as the player, where the system knows of owners it is theirs only
                try {
                    Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
                } catch (UnsupportedOperationException ignored) {
                }
            }
            Files.writeString(file, BedrockAuthManager.toJson(xboxAccount).toString());
        } catch (IOException e) {
            System.out.println("The xbox login of " + username + " can not be saved: " + e);
        }
    }
}
