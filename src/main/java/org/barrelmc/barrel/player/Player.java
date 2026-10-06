/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.nio.NioDatagramChannel;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.barrelmc.barrel.auth.LoginPayload;
import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.config.Config;
import org.barrelmc.barrel.entity.Entity;
import org.barrelmc.barrel.math.Vector3;
import org.barrelmc.barrel.network.BedrockBatchHandler;
import org.barrelmc.barrel.network.translator.PacketTranslatorManager;
import org.barrelmc.barrel.server.ProxyServer;
import org.barrelmc.barrel.utils.Utils;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.SimpleItemDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockClientInitializer;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Player extends Vector3 {

    @Getter
    private final Session javaSession;
    @Getter
    private BedrockClientSession bedrockSession;
    @Getter
    private final PacketTranslatorManager packetTranslatorManager;

    private BedrockAuthManager xboxAccount = null;
    @Getter
    private ECPublicKey publicKey;
    @Getter
    private ECPrivateKey privateKey;

    @Setter
    @Getter
    private long runtimeEntityId;
    // A server of mojang names an entity by this where it does not name it by its runtime id, the two are not the same
    @Setter
    @Getter
    private long uniqueEntityId;
    @Getter
    private String username;
    @Getter
    private String xuid;
    // The id of the player at the service the accounts are kept by, a number in hexadecimal
    private String playFabId = "";
    @Getter
    private String loginDescription = "";

    // Servers went by a chain of certificates before there were tokens. A client of this version sends one that
    // is nothing in its place, a server does not take a login that has none at all
    private static final List<String> NO_CERTIFICATES = Collections.singletonList("..");
    @Getter
    private String UUID;

    @Setter
    @Getter
    private int scoreSortorder;

    @Setter
    @Getter
    private StartGamePacket startGamePacketCache;

    private boolean tickPlayerInputStarted = false;
    private final ScheduledExecutorService playerInputExecutor = Executors.newScheduledThreadPool(1);

    @Setter
    @Getter
    private Vector3f oldPosition;

    @Setter
    @Getter
    private Vector3f lastServerPosition;

    @Setter
    @Getter
    private Vector2f lastServerRotation;

    @Setter
    @Getter
    private boolean isImmobile = false;

    @Setter
    @Getter
    private boolean isSneaking = false;
    @Setter
    @Getter
    private boolean isSprinting = false;
    @Setter
    @Getter
    private PlayerActionType diggingStatus;
    @Setter
    @Getter
    private Vector3i diggingPosition;
    @Setter
    @Getter
    private Direction diggingFace;

    @Setter
    private GameType gameMode = GameType.ADVENTURE;
    // The game mode of the world, a player whose game mode is DEFAULT plays in it
    @Setter
    private GameType levelGameMode = GameType.SURVIVAL;

    @Getter
    private final Set<PlayerAuthInputData> playerAuthInputData = EnumSet.noneOf(PlayerAuthInputData.class);
    @Getter
    private final List<PlayerBlockActionData> playerAuthInputActions = new ObjectArrayList<>();

    @Getter
    private final Inventory inventory = new Inventory(this);
    @Getter
    private final SubChunkRequests subChunkRequests = new SubChunkRequests(this);
    // The entities the java client was told about, by their bedrock runtime id
    @Getter
    private final Map<Long, Entity> entities = new HashMap<>();
    // The runtime ids of these entities by their unique id, a server removes an entity by that
    @Getter
    private final Map<Long, Long> entityRuntimeIds = new HashMap<>();
    // The names the bedrock server gave its item ids
    @Getter
    private final Map<Integer, ItemDefinition> itemDefinitions = new ConcurrentHashMap<>();

    @Getter
    @Setter
    private int renderDistance = 8;

    @Getter
    @Setter
    private long overworldClockId = -1;

    // What the java client is called. A player that is signed in to xbox is called what its account is
    @Getter
    private final String javaUsername;

    // The player itself as an entity, for what the java client is told about it like about any other
    @Getter
    private final Entity self = new Entity(EntityType.PLAYER);

    // What the hearts and the hunger bar of the java client show
    @Getter
    @Setter
    private float health = 20;
    @Getter
    @Setter
    private int food = 20;
    @Getter
    @Setter
    private float saturation = 5;

    // The effects the player has, with their strength
    @Getter
    private final Map<Effect, Integer> effects = new HashMap<>();

    @Getter
    @Setter
    private int experienceLevel = 0;
    @Getter
    @Setter
    private float experienceProgress = 0;

    public Player(ServerboundHelloPacket loginPacket, Session javaSession) {
        this.javaUsername = loginPacket.getUsername();
        this.packetTranslatorManager = new PacketTranslatorManager(this);
        this.javaSession = javaSession;

        if (ProxyServer.getInstance().getConfig().getAuth().equals("offline")) {
            // A server that does not check the accounts tells the players apart by these, PowerNukkitX by the xuid
            // alone. They are made of the name, so that a player is the same one every time and nobody else
            this.username = loginPacket.getUsername();
            java.util.UUID offlineUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.username).getBytes(StandardCharsets.UTF_8));
            this.UUID = offlineUuid.toString();
            this.xuid = Long.toString(offlineUuid.getMostSignificantBits() >>> 14);
            this.playFabId = Long.toHexString(offlineUuid.getLeastSignificantBits());
        } else {
            this.xboxAccount = AuthManager.getInstance().getXboxAccount(loginPacket.getUsername());
        }

        ProxyServer.getInstance().getOnlinePlayers().put(loginPacket.getUsername(), this);
    }

    public void startSendingPlayerInput() {
        if (!tickPlayerInputStarted) {
            tickPlayerInputStarted = true;

            PlayerAuthInputThread playerAuthInputThread = new PlayerAuthInputThread();
            playerAuthInputThread.player = this;
            playerAuthInputThread.tick = getStartGamePacketCache().getCurrentTick();

            playerInputExecutor.scheduleAtFixedRate(playerAuthInputThread, 0, 50, TimeUnit.MILLISECONDS);
        }
    }

    public void connect() {
        Config config = ProxyServer.getInstance().getConfig();
        BedrockCodec codec = ProxyServer.getInstance().getBedrockPacketCodec();

        new Bootstrap()
                .channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .group(ProxyServer.getInstance().getBedrockEventLoopGroup())
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, codec.getRaknetProtocolVersion())
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession session) {
                        bedrockSession = session;
                        if (!javaSession.isConnected()) {
                            session.disconnect();
                            return;
                        }

                        session.setCodec(codec);
                        // The default limits are too low for what a server sends
                        session.getPeer().getCodecHelper().setEncodingSettings(EncodingSettings.CLIENT);
                        // Barrel does not keep the block palette, runtime ids are translated as they are
                        session.getPeer().getCodecHelper().setBlockDefinitions(new DefinitionRegistry<>() {
                            @Override
                            public BlockDefinition getDefinition(int runtimeId) {
                                return () -> runtimeId;
                            }

                            @Override
                            public boolean isRegistered(BlockDefinition definition) {
                                return true;
                            }
                        });
                        session.getPeer().getCodecHelper().setItemDefinitions(new DefinitionRegistry<>() {
                            @Override
                            public ItemDefinition getDefinition(int runtimeId) {
                                ItemDefinition itemDefinition = itemDefinitions.get(runtimeId);
                                return itemDefinition == null ? new SimpleItemDefinition("", runtimeId, false) : itemDefinition;
                            }

                            @Override
                            public ItemDefinition getDefinition(String identifier) {
                                return new SimpleItemDefinition(identifier, 0, false);
                            }

                            @Override
                            public boolean isRegistered(ItemDefinition definition) {
                                return true;
                            }
                        });
                        session.setPacketHandler(new BedrockBatchHandler(Player.this));

                        RequestNetworkSettingsPacket requestNetworkSettingsPacket = new RequestNetworkSettingsPacket();
                        requestNetworkSettingsPacket.setProtocolVersion(codec.getProtocolVersion());
                        session.sendPacketImmediately(requestNetworkSettingsPacket);
                    }
                })
                .connect(new InetSocketAddress(config.getBedrockAddress(), config.getBedrockPort()))
                .addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        javaSession.disconnect("Server offline " + future.cause());
                    }
                });
    }

    public GameType getGameMode() {
        if (this.gameMode != GameType.DEFAULT) {
            return this.gameMode;
        }
        return this.levelGameMode == GameType.DEFAULT ? GameType.SURVIVAL : this.levelGameMode;
    }

    // Whether the player plays in the game mode of the world, whatever that is
    public boolean hasLevelGameMode() {
        return this.gameMode == GameType.DEFAULT;
    }

    public LoginPacket getOnlineLoginPacket() throws Exception {
        LoginPacket loginPacket = new LoginPacket();

        // The token is bound to the key pair of the Xbox Live session
        KeyPair ecdsa384KeyPair = this.xboxAccount.getSessionKeyPair();
        this.publicKey = (ECPublicKey) ecdsa384KeyPair.getPublic();
        this.privateKey = (ECPrivateKey) ecdsa384KeyPair.getPrivate();

        MinecraftMultiplayerToken token = this.xboxAccount.getMinecraftMultiplayerToken().getUpToDate();
        this.username = token.getDisplayName();
        this.xuid = token.getXuid();
        this.UUID = token.getUuid().toString();

        JSONObject clientData = this.getClientData();
        this.describeLogin(AuthType.FULL, NO_CERTIFICATES, token.getToken(), clientData);
        loginPacket.setAuthPayload(new LoginPayload(AuthType.FULL, NO_CERTIFICATES, token.getToken()));
        loginPacket.setClientJwt(this.signClientData(clientData));
        loginPacket.setProtocolVersion(ProxyServer.getInstance().getBedrockPacketCodec().getProtocolVersion());
        return loginPacket;
    }

    private static JSONObject parseJwt(String jwt, int part) {
        return JSONObject.parseObject(new String(Base64.getUrlDecoder().decode(jwt.split("\\.")[part]), StandardCharsets.UTF_8));
    }

    // What a login is made of without what is in it, for when a server does not take it
    private void describeLogin(AuthType authType, List<String> certificates, String token, JSONObject clientData) {
        StringBuilder fields = new StringBuilder();
        for (Map.Entry<String, Object> field : clientData.entrySet()) {
            Object value = field.getValue();
            fields.append(' ').append(field.getKey()).append(value instanceof String ? ":" + ((String) value).length() : value instanceof Collection ? "[]" : "=" + value);
        }
        this.loginDescription = "type " + authType + ", certificates " + certificates + ", token header " + parseJwt(token, 0).keySet() + " claims " + parseJwt(token, 1).keySet()
                + ", protocol " + ProxyServer.getInstance().getBedrockPacketCodec().getProtocolVersion() + ", client data:" + fields;
    }

    public LoginPacket getLoginPacket() {
        LoginPacket loginPacket = new LoginPacket();

        KeyPair ecdsa384KeyPair = EncryptionUtils.createKeyPair();
        this.publicKey = (ECPublicKey) ecdsa384KeyPair.getPublic();
        this.privateKey = (ECPrivateKey) ecdsa384KeyPair.getPrivate();

        String publicKeyBase64 = Base64.getEncoder().encodeToString(this.publicKey.getEncoded());

        // A client that is not signed in makes the token of an account itself. Before 1.26.10 it was a certificate
        JSONObject token = new JSONObject();
        token.put("aud", "api://auth-minecraft-services/multiplayer");
        token.put("exp", Instant.now().getEpochSecond() + TimeUnit.HOURS.toSeconds(6));
        token.put("nbf", Instant.now().getEpochSecond() - TimeUnit.HOURS.toSeconds(6));
        token.put("ipt", "");
        token.put("mid", this.playFabId);
        token.put("tid", "");
        token.put("cpk", publicKeyBase64);
        token.put("xid", this.xuid);
        token.put("xname", this.username);
        token.put("leguuid", this.UUID);

        JSONObject jwtHeader = new JSONObject();
        jwtHeader.put("alg", "ES384");
        jwtHeader.put("x5u", publicKeyBase64);

        JSONObject clientData = this.getClientData();
        String signedToken = generateJwt(jwtHeader, token);
        this.describeLogin(AuthType.SELF_SIGNED, NO_CERTIFICATES, signedToken, clientData);
        loginPacket.setAuthPayload(new LoginPayload(AuthType.SELF_SIGNED, NO_CERTIFICATES, signedToken));
        loginPacket.setClientJwt(this.signClientData(clientData));
        loginPacket.setProtocolVersion(ProxyServer.getInstance().getBedrockPacketCodec().getProtocolVersion());
        return loginPacket;
    }

    private String signClientData(JSONObject clientData) {
        JSONObject jwtHeader = new JSONObject();
        jwtHeader.put("alg", "ES384");
        jwtHeader.put("x5u", Base64.getEncoder().encodeToString(this.publicKey.getEncoded()));
        return generateJwt(jwtHeader, clientData);
    }

    // What a client tells about itself and its skin when it joins. A server of mojang does not take a login it
    // does not like this of, and does not say what it is. So these are the fields and the kind of values of a
    // client that is known to be taken, no more and no less: servers do not know ThirdPartyNameOnly and PlayFabId
    // anymore
    private JSONObject getClientData() {
        JSONObject skinData = new JSONObject();

        skinData.put("AnimatedImageData", new JSONArray());
        skinData.put("ArmSize", "wide");
        skinData.put("CapeData", "");
        skinData.put("CapeId", "");
        skinData.put("CapeImageHeight", 0);
        skinData.put("CapeImageWidth", 0);
        skinData.put("CapeOnClassicSkin", false);
        skinData.put("ClientEditorConnectionIntent", 0);
        skinData.put("ClientIsEditorCapable", false);
        // Servers read it as a number without a sign
        skinData.put("ClientRandomId", new Random().nextLong() >>> 1);
        skinData.put("CompatibleWithClientSideChunkGen", false);
        skinData.put("CurrentInputMode", 1);
        skinData.put("DefaultInputMode", 1);
        // The game for windows, it writes the id of a device without dashes. There is no 7 anymore, that was the
        // game from the store of windows 10
        skinData.put("DeviceId", java.util.UUID.randomUUID().toString().replace("-", ""));
        skinData.put("DeviceModel", "Barrel");
        skinData.put("DeviceOS", 8);
        skinData.put("FilterProfanity", false);
        skinData.put("GameVersion", ProxyServer.getInstance().getBedrockPacketCodec().getMinecraftVersion());
        skinData.put("GraphicsMode", 1);
        skinData.put("GuiScale", -1);
        skinData.put("LanguageCode", "en_US");
        skinData.put("MaxViewDistance", this.renderDistance);
        // The highest every server knows, some count from one below
        skinData.put("MemoryTier", 4);
        skinData.put("OverrideSkin", false);
        skinData.put("PersonaPieces", new JSONArray());
        skinData.put("PersonaSkin", false);
        skinData.put("PieceTintColors", new JSONArray());
        skinData.put("PlatformOfflineId", "");
        skinData.put("PlatformOnlineId", "");
        skinData.put("PlatformType", 0);
        skinData.put("PremiumSkin", false);
        skinData.put("SelfSignedId", this.UUID);
        skinData.put("ServerAddress", ProxyServer.getInstance().getConfig().getBedrockAddress() + ":" + ProxyServer.getInstance().getConfig().getBedrockPort());
        skinData.put("SkinAnimationData", "");
        skinData.put("SkinColor", "#0");
        skinData.put("SkinData", ProxyServer.getInstance().getDefaultSkinData());
        skinData.put("SkinGeometryData", Base64.getEncoder().encodeToString(ProxyServer.getInstance().getDefaultSkinGeometry().getBytes()));
        skinData.put("SkinId", java.util.UUID.randomUUID().toString());
        skinData.put("SkinImageHeight", 64);
        skinData.put("SkinImageWidth", 64);
        skinData.put("SkinResourcePatch", Base64.getEncoder().encodeToString("{\"geometry\":{\"default\":\"geometry.humanoid.custom\"}}".getBytes(StandardCharsets.UTF_8)));
        skinData.put("ThirdPartyName", this.username);
        skinData.put("UIProfile", 0);
        skinData.put("IsEditorMode", false);
        skinData.put("TrustedSkin", false);
        skinData.put("SkinGeometryDataEngineVersion", Base64.getEncoder().encodeToString("0.0.0".getBytes(StandardCharsets.UTF_8)));
        // Clients send this since 1.26.40, a server does not take a login without it
        skinData.put("ProfileHash", "");

        return skinData;
    }

    private String generateJwt(JSONObject jwtHeader, JSONObject chain) {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(jwtHeader.toJSONString().getBytes());
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(chain.toJSONString().getBytes());

        byte[] dataToSign = (header + "." + payload).getBytes();
        byte[] signatureBytes = null;
        try {
            Signature signature = Signature.getInstance("SHA384withECDSA");
            signature.initSign(this.privateKey);
            signature.update(dataToSign);
            signatureBytes = Utils.DERToJOSE(signature.sign(), Utils.AlgorithmType.ECDSA384);
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException ignored) {
        }
        String signatureString = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return header + "." + payload + "." + signatureString;
    }

    public void sendMessage(String message) {
        this.javaSession.send(new ClientboundSystemChatPacket(Component.text(message), false));
    }

    public void sendTip(String message) {
        this.javaSession.send(new ClientboundSystemChatPacket(Component.text(message), true));
    }

    public void disconnect(String reason) {
        playerInputExecutor.shutdown();
        packetTranslatorManager.shutdown();
        if (this.bedrockSession != null && this.bedrockSession.isConnected()) {
            this.bedrockSession.disconnect();
        }
        this.javaSession.disconnect(reason);
        ProxyServer.getInstance().getOnlinePlayers().values().remove(this);
    }

    @Override
    public void setPosition(Vector3f vector3f) {
        if (this.getFloorX() >> 4 != vector3f.getFloorX() >> 4 || this.getFloorZ() >> 4 != vector3f.getFloorZ() >> 4) {
            this.javaSession.send(new ClientboundSetChunkCacheCenterPacket(vector3f.getFloorX() >> 4, vector3f.getFloorZ() >> 4));
        }
        super.setPosition(vector3f);
    }

    @Override
    public void setPosition(double x, double y, double z) {
        if (this.getFloorX() >> 4 != (int) x >> 4 || this.getFloorZ() >> 4 != (int) z >> 4) {
            this.javaSession.send(new ClientboundSetChunkCacheCenterPacket((int) x >> 4, (int) z >> 4));
        }
        super.setPosition(x, y, z);
    }
}

class PlayerAuthInputThread implements Runnable {
    public Player player;
    public long tick;

    public void run() {
        try {
            if (player.getBedrockSession().isConnected()) {
                ++tick;

                PlayerAuthInputPacket pk = new PlayerAuthInputPacket();

                pk.setPosition(player.getVector3f());
                pk.setRotation(Vector3f.from(player.getPitch(), player.getYaw(), player.getYaw()));
                pk.setMotion(Vector2f.ZERO);
                pk.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
                pk.setInputMode(InputMode.MOUSE);
                pk.setPlayMode(ClientPlayMode.SCREEN);
                pk.setVrGazeDirection(null);
                pk.setInteractRotation(Vector2f.from(player.getPitch(), player.getYaw()));
                pk.setTick(tick);
                pk.setDelta(Vector3f.from(player.getVector3f().getX() - player.getOldPosition().getX(), player.getVector3f().getY() - player.getOldPosition().getY(), player.getVector3f().getZ() - player.getOldPosition().getZ()));
                pk.setAnalogMoveVector(Vector2f.ZERO);
                pk.setRawMoveVector(Vector2f.ZERO);
                pk.setCameraOrientation(player.getDirectionVector());
                pk.setItemStackRequest(null);

                pk.getInputData().addAll(player.getPlayerAuthInputData());
                pk.getPlayerActions().addAll(player.getPlayerAuthInputActions());

                if (player.isSneaking()) {
                    pk.getInputData().add(PlayerAuthInputData.SNEAKING);
                }
                if (player.isSprinting()) {
                    pk.getInputData().add(PlayerAuthInputData.SPRINTING);
                }
                if (player.getDiggingStatus() == PlayerActionType.START_BREAK) {
                    pk.getInputData().add(PlayerAuthInputData.PERFORM_BLOCK_ACTIONS);

                    PlayerBlockActionData blockActionData = new PlayerBlockActionData();
                    blockActionData.setAction(PlayerActionType.CONTINUE_BREAK);
                    blockActionData.setBlockPosition(player.getDiggingPosition());
                    blockActionData.setFace(player.getDiggingFace().ordinal());
                    pk.getPlayerActions().add(blockActionData);
                }

                player.getBedrockSession().sendPacketImmediately(pk);

                player.getPlayerAuthInputData().removeAll(player.getPlayerAuthInputData());
                player.getPlayerAuthInputActions().removeAll(player.getPlayerAuthInputActions());

                if (player.getInventory().tickItemUse()) {
                    // The inventory belongs to the thread that translates the packets
                    player.getPacketTranslatorManager().execute(() -> player.getInventory().finishUsingItem());
                }
                // Twice a second is often enough to ask again for what the server did not have
                if (tick % 10 == 0 && player.getSubChunkRequests().isWaiting()) {
                    player.getPacketTranslatorManager().execute(() -> player.getSubChunkRequests().tick());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
