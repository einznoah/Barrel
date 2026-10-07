/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.player;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
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
import org.barrelmc.barrel.network.nethernet.NetherNetInitializer;
import org.barrelmc.barrel.network.nethernet.NetherNetProbe;
import org.barrelmc.barrel.network.nethernet.NetherNetServerTrust;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChannelFactory;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;
import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;
import org.cloudburstmc.netty.channel.nethernet.signaling.NetherNetHTTPClientSignaling;
import org.cloudburstmc.netty.util.nethernet.OperatorIdentity;
import tel.schich.libdatachannel.LibDataChannelArchDetect;
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
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Player extends Vector3 {

    @Getter
    private final Session javaSession;
    @Getter
    private BedrockClientSession bedrockSession;
    // The connection to the bedrock server, from the moment it is asked for
    private volatile Channel bedrockChannel;
    @Getter
    private final PacketTranslatorManager packetTranslatorManager;

    private BedrockAuthManager xboxAccount = null;
    @Getter
    private ECPublicKey publicKey;
    @Getter
    private ECPrivateKey privateKey;
    private String offlineToken;
    // Whether the webrtc of a nethernet connection has been loaded
    private static boolean netherNetLoaded;
    // Where the tokens of players come from, which a nethernet server is told with the token
    private static final String NETHERNET_AUTH_DOMAIN = "authorization.franchise.minecraft-services.net";
    // The kind of device the proxy says it is: the game for windows
    private static final int DEVICE_OS = 8;

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
    private boolean flying = false;
    // Whether the server has been told that the player is done loading the world it joined
    @Setter
    @Getter
    private boolean spawned = false;
    // The dimension the player is in, by the number the bedrock server has for it
    @Setter
    @Getter
    private int dimension = 0;
    // The server is taking the player to another dimension and has not told yet that it is done
    @Setter
    @Getter
    private boolean changingDimension = false;
    // The loading screen the server named for that, a server names none for some changes
    @Setter
    @Getter
    private Integer dimensionLoadingScreen = null;
    // The server was not answered yet that the player has arrived in the dimension
    @Setter
    @Getter
    private boolean dimensionAnswerOwed = false;
    // The java client asked to come back to life and the server has not found where yet
    @Setter
    @Getter
    private boolean respawning = false;
    // What the java client was last told the player may do, and the effects on the player as it was told them with
    // the time. A java client forgets both when the player comes into another dimension
    @Setter
    @Getter
    private ClientboundPlayerAbilitiesPacket abilities = null;
    @Getter
    private final Map<Effect, SentEffect> sentEffects = new HashMap<>();
    @Getter
    private final PlayerInput input = new PlayerInput(this);
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
    @Getter
    private final BedrockBlocks bedrockBlocks = new BedrockBlocks(this);
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
    // The java account of the player. Without javaAuth it is made of the name
    @Getter
    private final java.util.UUID javaUuid;

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

    public Player(GameProfile profile, Session javaSession) {
        this.javaUsername = profile.getName();
        this.javaUuid = profile.getId();
        this.packetTranslatorManager = new PacketTranslatorManager(this);
        this.javaSession = javaSession;

        if (ProxyServer.getInstance().getConfig().getAuth().equals("offline")) {
            // A server that does not check the accounts tells the players apart by these, PowerNukkitX by the xuid
            // alone. They are made of the name, so that a player is the same one every time and nobody else
            this.username = profile.getName();
            java.util.UUID offlineUuid = java.util.UUID.nameUUIDFromBytes(("OfflinePlayer:" + this.username).getBytes(StandardCharsets.UTF_8));
            this.UUID = offlineUuid.toString();
            this.xuid = Long.toString(offlineUuid.getMostSignificantBits() >>> 14);
            this.playFabId = Long.toHexString(offlineUuid.getLeastSignificantBits());
        } else {
            this.xboxAccount = AuthManager.getInstance().getXboxAccount(profile.getId());
        }

        ProxyServer.getInstance().getOnlinePlayers().put(profile.getName(), this);
    }

    // For what does not wait for a packet. It runs on the thread that translates the packets
    public void runEveryTick(Runnable task) {
        playerInputExecutor.scheduleAtFixedRate(() -> packetTranslatorManager.executeFirst(task), 50, 50, TimeUnit.MILLISECONDS);
    }

    public void startSendingPlayerInput() {
        this.input.start(getStartGamePacketCache().getCurrentTick());
    }

    public void connect() {
        Config config = ProxyServer.getInstance().getConfig();
        BedrockCodec codec = ProxyServer.getInstance().getBedrockPacketCodec();
        InetSocketAddress address = new InetSocketAddress(config.getBedrockAddress(), config.getBedrockPort());
        Bootstrap bootstrap = new Bootstrap().group(ProxyServer.getInstance().getBedrockEventLoopGroup());

        if (config.getTransport().equalsIgnoreCase("nethernet")) {
            // Who joins is told before there is a connection, and asking for the token of a player can take a moment
            CompletableFuture.runAsync(() -> {
                try {
                    this.connectNetherNet(bootstrap, address, codec);
                } catch (Throwable e) {
                    System.out.println("Could not join the server over nethernet: " + e + " [player " + this.getUsername() + "]");
                    javaSession.disconnect("Could not join the server over nethernet: " + (e instanceof ConnectException ? e.getMessage() : e.toString()));
                }
            });
            return;
        }

        bootstrap.channelFactory(RakChannelFactory.client(NioDatagramChannel.class))
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, codec.getRaknetProtocolVersion())
                // A server tells its connections apart by this. The library leaves it at 0, and a server of mojang
                // does not take a second connection with a number that is connected already
                .option(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong())
                .handler(new BedrockClientInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession session) {
                        Player.this.initSession(session, codec);
                    }
                });
        this.connect(bootstrap, address);
    }

    private void connect(Bootstrap bootstrap, InetSocketAddress address) {
        ChannelFuture connecting = bootstrap.connect(address);
        this.bedrockChannel = connecting.channel();
        connecting.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                javaSession.disconnect("Server offline " + future.cause());
            }
        });
    }

    // Ends the connection to the bedrock server and waits, for no longer than the given time, until it is gone
    public void closeBedrockConnection(long millis) {
        Channel channel = this.bedrockChannel;
        if (channel == null) {
            return;
        }
        if (this.bedrockSession != null && this.bedrockSession.isConnected()) {
            this.bedrockSession.disconnect();
        } else {
            channel.close();
        }
        channel.closeFuture().awaitUninterruptibly(millis);
    }

    // A server that is reached over nethernet: the address is where it answers to http, there it is asked to let the
    // player in and tells where the connection itself goes
    private void connectNetherNet(Bootstrap bootstrap, InetSocketAddress address, BedrockCodec codec) throws Exception {
        synchronized (Player.class) {
            if (!netherNetLoaded) {
                // The webrtc a nethernet connection is made of is not written in java
                LibDataChannelArchDetect.initialize();
                netherNetLoaded = true;
            }
        }

        // The game first asks a server whether it is one of nethernet, and so whether it answers to https or to
        // http. The library would ask with more in the address than the game does, which a server of mojang
        // answers as not found, and takes no answer with nothing in it, which is what a server gives that does not
        // show itself to the players around it. So it is asked here and the library is told what was found
        HttpSignalingSettings settings = HttpSignalingSettings.DEFAULT.withScheme(NetherNetProbe.probe(address));
        bootstrap.channelFactory(NetherNetChannelFactory.client(new NetherNetHTTPClientSignaling(settings)))
                .option(NetherChannelOption.NETHER_CLIENT_IDENTITY, this.getNetherNetIdentity())
                .option(NetherChannelOption.NETHER_CLIENT_SERVER_TRUST, NetherNetServerTrust.INSTANCE)
                .handler(new NetherNetInitializer() {
                    @Override
                    protected void initSession(BedrockClientSession session) {
                        Player.this.initSession(session, codec);
                    }
                });
        this.connect(bootstrap, address);
    }

    // Who the player is, as the login tells it later: the token of the account and the key the token is for. With it
    // the server is told that the connection is one of this player
    private OperatorIdentity getNetherNetIdentity() throws Exception {
        if (this.xboxAccount == null) {
            return OperatorIdentity.fromToken(this.getOfflineKeyPair(), this.getOfflineToken(), NETHERNET_AUTH_DOMAIN);
        }

        String token = this.xboxAccount.getMinecraftMultiplayerToken().getUpToDate().getToken();
        String issuer = parseJwt(token, 1).getString("iss");
        String domain = issuer == null ? null : URI.create(issuer).getHost();
        return OperatorIdentity.fromToken(this.xboxAccount.getSessionKeyPair(), token, domain == null ? NETHERNET_AUTH_DOMAIN : domain);
    }

    private void initSession(BedrockClientSession session, BedrockCodec codec) {
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
        session.setPacketHandler(new BedrockBatchHandler(this));

        RequestNetworkSettingsPacket requestNetworkSettingsPacket = new RequestNetworkSettingsPacket();
        requestNetworkSettingsPacket.setProtocolVersion(codec.getProtocolVersion());
        session.sendPacketImmediately(requestNetworkSettingsPacket);
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

    private KeyPair getOfflineKeyPair() {
        if (this.privateKey == null) {
            KeyPair ecdsa384KeyPair = EncryptionUtils.createKeyPair();
            this.publicKey = (ECPublicKey) ecdsa384KeyPair.getPublic();
            this.privateKey = (ECPrivateKey) ecdsa384KeyPair.getPrivate();
        }
        return new KeyPair(this.publicKey, this.privateKey);
    }

    // A client that is not signed in makes the token of an account itself. Before 1.26.10 it was a certificate
    private String getOfflineToken() {
        if (this.offlineToken != null) {
            return this.offlineToken;
        }

        String publicKeyBase64 = Base64.getEncoder().encodeToString(this.getOfflineKeyPair().getPublic().getEncoded());

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

        this.offlineToken = generateJwt(jwtHeader, token);
        return this.offlineToken;
    }

    public LoginPacket getLoginPacket() {
        LoginPacket loginPacket = new LoginPacket();

        String signedToken = this.getOfflineToken();
        JSONObject clientData = this.getClientData();
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
        skinData.put("DeviceOS", DEVICE_OS);
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

    public record SentEffect(ClientboundUpdateMobEffectPacket packet, long time) {
    }

    public ProxyServer.Dimension getJavaDimension() {
        return ProxyServer.getInstance().getDimension(this.dimension);
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
