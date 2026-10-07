/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.server;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.barrelmc.barrel.auth.LoginSerializer;
import org.barrelmc.barrel.network.CommandOutputSerializer;
import org.barrelmc.barrel.Barrel;
import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.auth.server.AuthServer;
import org.barrelmc.barrel.config.Config;
import org.barrelmc.barrel.network.JavaPacketHandler;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.utils.FileManager;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockPacketDefinition;
import org.cloudburstmc.protocol.bedrock.codec.v2193.Bedrock_v2193;
import org.cloudburstmc.protocol.bedrock.data.PacketRecipient;
import org.cloudburstmc.protocol.bedrock.packet.CommandOutputPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.PacketViolationWarningPacket;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.SessionService;
import org.geysermc.mcprotocollib.network.Server;
import org.geysermc.mcprotocollib.network.event.server.ServerAdapter;
import org.geysermc.mcprotocollib.network.event.server.ServerClosedEvent;
import org.geysermc.mcprotocollib.network.event.server.SessionAddedEvent;
import org.geysermc.mcprotocollib.network.event.server.SessionRemovedEvent;
import org.geysermc.mcprotocollib.network.server.NetworkServer;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.ServerLoginHandler;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftCodec;
import org.geysermc.mcprotocollib.protocol.data.status.PlayerInfo;
import org.geysermc.mcprotocollib.protocol.data.status.ServerStatusInfo;
import org.geysermc.mcprotocollib.protocol.data.status.VersionInfo;
import org.geysermc.mcprotocollib.protocol.data.status.handler.ServerInfoBuilder;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ProxyServer {

    @Getter
    private static ProxyServer instance = null;
    @Getter
    private final Map<String, Player> onlinePlayers = new ConcurrentHashMap<>();
    @Getter
    private final BedrockCodec bedrockPacketCodec = acceptViolationWarnings(Bedrock_v2193.CODEC).toBuilder().updateSerializer(LoginPacket.class, new LoginSerializer()).updateSerializer(CommandOutputPacket.class, new CommandOutputSerializer()).build();
    @Getter
    private final EventLoopGroup bedrockEventLoopGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

    @Getter
    private final Path dataPath;

    @Getter
    private Config config;

    @Getter
    private String defaultSkinData;
    @Getter
    private String defaultSkinGeometry;

    // Network ids in the registries MCProtocolLib sends to the client while it is configuring
    @Getter
    private final int overworldId;
    @Getter
    private final int overworldMinSection;
    @Getter
    private final int overworldSectionCount;
    @Getter
    private final int overworldClockId;
    // The dimensions by the numbers a bedrock server has for them: the overworld, the nether and the end
    private final Dimension[] dimensions;
    // The paintings the java client was told of, by their names
    private final Map<String, Painting> paintings = new HashMap<>();
    @Getter
    private final int defaultBiomeId;
    @Getter
    private final int biomeCount;

    public ProxyServer(String dataPath) {
        instance = this;
        this.dataPath = Paths.get(dataPath);
        if (!this.initConfig()) {
            System.out.println("Config file not found! Terminating...");
            System.exit(0);
        }

        NbtMap registries = MinecraftProtocol.loadNetworkCodec();
        NbtMap overworld = getRegistryEntry(registries, "minecraft:dimension_type", "minecraft:overworld");
        this.overworldId = overworld.getInt("id");
        this.overworldMinSection = overworld.getCompound("element").getInt("min_y") >> 4;
        this.overworldSectionCount = overworld.getCompound("element").getInt("height") >> 4;
        this.overworldClockId = getRegistryEntry(registries, "minecraft:world_clock", "minecraft:overworld").getInt("id");
        this.dimensions = new Dimension[]{readDimension(registries, "minecraft:overworld"), readDimension(registries, "minecraft:the_nether"), readDimension(registries, "minecraft:the_end")};
        for (NbtMap painting : registries.getCompound("minecraft:painting_variant").getList("value", NbtType.COMPOUND)) {
            NbtMap element = painting.getCompound("element");
            this.paintings.put(painting.getString("name"), new Painting(painting.getInt("id"), element.getInt("width"), element.getInt("height")));
        }
        this.defaultBiomeId = getRegistryEntry(registries, "minecraft:worldgen/biome", "minecraft:plains").getInt("id");
        this.biomeCount = registries.getCompound("minecraft:worldgen/biome").getList("value", NbtType.COMPOUND).size();

        try {
            defaultSkinData = FileManager.getFileContents(Objects.requireNonNull(Barrel.class.getClassLoader().getResourceAsStream("skin/skin_data.txt")));
            defaultSkinGeometry = FileManager.getFileContents(Objects.requireNonNull(Barrel.class.getClassLoader().getResourceAsStream("skin/skin_geometry.json")));
        } catch (Exception e) {
            e.printStackTrace();
        }

        this.startServer();
    }

    // What the java client was told of a dimension: the number of its kind, its name, its lowest section, how many
    // sections it has and whether the sky gives light in it
    public record Dimension(int id, Key name, int minSection, int sectionCount, boolean skyLight) {
    }

    private static Dimension readDimension(NbtMap registries, String name) {
        NbtMap entry = getRegistryEntry(registries, "minecraft:dimension_type", name);
        NbtMap element = entry.getCompound("element");
        return new Dimension(entry.getInt("id"), Key.key(name), element.getInt("min_y") >> 4, element.getInt("height") >> 4, element.getBoolean("has_skylight"));
    }

    // What the java client was told of a painting: its number, and how many blocks wide and high it is
    public record Painting(int id, int width, int height) {
    }

    public Painting getPainting(String name) {
        return this.paintings.get(name);
    }

    // A dimension that is not known is taken for the overworld
    public Dimension getDimension(int bedrockDimension) {
        return this.dimensions[bedrockDimension >= 0 && bedrockDimension < this.dimensions.length ? bedrockDimension : 0];
    }

    private static NbtMap getRegistryEntry(NbtMap registries, String registry, String name) {
        for (NbtMap entry : registries.getCompound(registry).getList("value", NbtType.COMPOUND)) {
            if (entry.getString("name").equals(name)) {
                return entry;
            }
        }

        throw new IllegalStateException(name + " is missing from the " + registry + " registry");
    }

    // The warning about a packet that was not understood is one clients send. Some servers send it too before they
    // close the connection, it tells which packet of the proxy they did not take
    private static BedrockCodec acceptViolationWarnings(BedrockCodec codec) {
        BedrockPacketDefinition<PacketViolationWarningPacket> definition = codec.getPacketDefinition(PacketViolationWarningPacket.class);
        return codec.toBuilder()
                .deregisterPacket(PacketViolationWarningPacket.class)
                .registerPacket(PacketViolationWarningPacket::new, definition.getSerializer(), definition.getId(), PacketRecipient.BOTH)
                .build();
    }

    private boolean initConfig() {
        try {
            InputStream inputStream = new FileInputStream(this.dataPath.toString() + "/config.yml");
            this.config = (new Yaml()).loadAs(inputStream, Config.class);
            return true;
        } catch (FileNotFoundException e) {
            try {
                InputStream inputStream = new FileInputStream("./src/main/resources/config.yml");
                this.config = (new Yaml()).loadAs(inputStream, Config.class);
                return true;
            } catch (FileNotFoundException ignored) {
            }
        }

        return false;
    }

    // When the proxy is stopped, the servers are told that their players leave. A nethernet connection that is
    // still open would also keep the proxy from stopping at all: the webrtc it is made of waits for it
    private void closeBedrockConnections() {
        for (Player player : new ArrayList<>(this.onlinePlayers.values())) {
            try {
                player.closeBedrockConnection(2000);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    // Whether a java player has to be signed in to the account it joins with. Anything but offline asks for it
    public boolean verifiesJavaAccounts() {
        return !"offline".equalsIgnoreCase(this.config.getJavaAuth());
    }

    private void startServer() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeBedrockConnections, "Barrel shutdown"));

        SessionService sessionService = new SessionService();

        Server server = new NetworkServer(new InetSocketAddress(this.config.getBindAddress(), this.config.getPort()), MinecraftProtocol::new);
        server.setGlobalFlag(MinecraftConstants.SESSION_SERVICE_KEY, sessionService);
        // With these the library asks mojang whether the client is signed in to the java account it names, and only
        // then tells who has joined. A client is only asked once the connection is encrypted
        boolean verifyJavaAccounts = this.verifiesJavaAccounts();
        server.setGlobalFlag(MinecraftConstants.ENCRYPT_CONNECTION, verifyJavaAccounts);
        server.setGlobalFlag(MinecraftConstants.SHOULD_AUTHENTICATE, verifyJavaAccounts);
        if (!verifyJavaAccounts) {
            System.out.println("Java accounts are not checked (javaAuth: offline): anybody can join under any name"
                    + (this.config.isRememberLogins() ? ", and plays with the xbox account that was remembered for it" : ""));
        }
        server.setGlobalFlag(MinecraftConstants.SERVER_INFO_BUILDER_KEY, (ServerInfoBuilder) session -> new ServerStatusInfo(Component.text(this.config.getMotd()), new PlayerInfo(10, 0, new ArrayList<>()), new VersionInfo(MinecraftCodec.CODEC.getMinecraftVersion(), MinecraftCodec.CODEC.getProtocolVersion()), null, false));
        server.setGlobalFlag(MinecraftConstants.SERVER_LOGIN_HANDLER_KEY, (ServerLoginHandler) session -> {
            GameProfile profile = session.getFlag(MinecraftConstants.PROFILE_KEY);
            System.out.println(profile.getName() + " logged in");

            // The client can only be sent game packets from here on, so this is when the bedrock server is joined
            Player player = getPlayerByName(profile.getName());
            if (player != null && player.getJavaSession() == session) {
                player.connect();
            } else {
                session.addListener(new AuthServer(session, profile.getId()));
            }
        });
        server.setGlobalFlag(MinecraftConstants.SERVER_COMPRESSION_THRESHOLD, 100);
        server.addListener(new ServerAdapter() {
            @Override
            public void serverClosed(ServerClosedEvent event) {
                for (var entry : ProxyServer.getInstance().getOnlinePlayers().entrySet()) {
                    Player player = entry.getValue();

                    player.disconnect("Proxy closed");
                }
                System.out.println("Server closed.");
            }

            @Override
            public void sessionAdded(SessionAddedEvent event) {
                event.getSession().addListener(new JavaPacketHandler());
            }

            @Override
            public void sessionRemoved(SessionRemovedEvent event) {
                GameProfile profile = event.getSession().getFlag(MinecraftConstants.PROFILE_KEY);
                if (profile == null) {
                    // Server list ping
                    return;
                }

                Thread loginThread = AuthManager.getInstance().getLoginThreads().remove(profile.getId());
                if (loginThread != null) {
                    loginThread.interrupt();
                }
                System.out.println(profile.getName() + " logged out");

                Player player = getPlayerByName(profile.getName());
                if (player != null && player.getJavaSession() == event.getSession()) {
                    player.disconnect("logged out");
                }
            }
        });

        System.out.println("Binding to " + this.config.getBindAddress() + " on port " + this.config.getPort());
        server.bind();
        System.out.println("BarrelProxy is running on [" + this.config.getBindAddress() + "::" + this.config.getPort() + "]");
    }

    public Player getPlayerByName(String username) {
        return this.onlinePlayers.get(username);
    }
}
