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
import org.geysermc.mcprotocollib.auth.GameProfile;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class AuthManager {

    private static final String ACCOUNTS_FOLDER = "accounts";
    // What a java player can be called. A login was saved under the name of its player before the java accounts
    // were told apart by their id, and only a file of such a name is looked for
    private static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private static AuthManager instance;

    // The xbox accounts the players signed in to, by the java account that signed in. A name can be given up and
    // taken by somebody else, the id of an account stays. Without javaAuth the id is made of the name
    private final Map<UUID, BedrockAuthManager> xboxAccounts = new ConcurrentHashMap<>();

    @Getter
    private final Map<UUID, Thread> loginThreads = new ConcurrentHashMap<>();

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

    private static Path getAccountFile(UUID javaAccount) {
        return Paths.get(Barrel.DATA_PATH, ACCOUNTS_FOLDER, javaAccount + ".json");
    }

    // A login that was saved under the name of its player is from now on the one of the java account of that name
    private static void takeLoginSavedByName(GameProfile javaAccount, Path file) {
        if (Files.exists(file) || !PLAYER_NAME.matcher(javaAccount.getName()).matches()) {
            return;
        }

        Path fileByName = Paths.get(Barrel.DATA_PATH, ACCOUNTS_FOLDER, javaAccount.getName() + ".json");
        try {
            if (Files.exists(fileByName)) {
                Files.move(fileByName, file);
            }
        } catch (IOException e) {
            System.out.println("The xbox login that was saved for the name " + javaAccount.getName() + " can not be taken over: " + e);
        }
    }

    // Whether the player does not have to sign in to xbox before it joins
    public boolean hasXboxAccount(GameProfile javaAccount) {
        if (this.xboxAccounts.containsKey(javaAccount.getId())) {
            return true;
        }
        if (!rememberLogins()) {
            return false;
        }

        Path file = getAccountFile(javaAccount.getId());
        takeLoginSavedByName(javaAccount, file);
        if (!Files.exists(file)) {
            return false;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            String bedrockVersion = ProxyServer.getInstance().getBedrockPacketCodec().getMinecraftVersion();
            this.addXboxAccount(javaAccount.getId(), BedrockAuthManager.fromJson(this.xboxLive.getHttpClient(), bedrockVersion, JsonParser.parseReader(reader).getAsJsonObject()));
            return true;
        } catch (Exception e) {
            System.out.println("The saved xbox login of " + javaAccount.getName() + " can not be read, the player has to sign in again: " + e);
            return false;
        }
    }

    public void addXboxAccount(UUID javaAccount, BedrockAuthManager xboxAccount) {
        this.xboxAccounts.put(javaAccount, xboxAccount);
        if (rememberLogins()) {
            // What a login is made of is renewed from time to time
            xboxAccount.getChangeListeners().add((BasicChangeListener) () -> saveXboxAccount(javaAccount, xboxAccount));
            saveXboxAccount(javaAccount, xboxAccount);
        }
    }

    // The account a player joins the bedrock server with. One that is not remembered is for one time
    public BedrockAuthManager getXboxAccount(UUID javaAccount) {
        return rememberLogins() ? this.xboxAccounts.get(javaAccount) : this.xboxAccounts.remove(javaAccount);
    }

    // For a login the server or xbox no longer takes, the player signs in again the next time
    public void forgetXboxAccount(UUID javaAccount) {
        this.xboxAccounts.remove(javaAccount);
        try {
            Files.deleteIfExists(getAccountFile(javaAccount));
        } catch (IOException e) {
            System.out.println("The saved xbox login of the java account " + javaAccount + " can not be deleted: " + e);
        }
    }

    private static synchronized void saveXboxAccount(UUID javaAccount, BedrockAuthManager xboxAccount) {
        Path file = getAccountFile(javaAccount);
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
            System.out.println("The xbox login of the java account " + javaAccount + " can not be saved: " + e);
        }
    }
}
