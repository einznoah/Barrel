package org.barrelmc.barrel.auth;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import org.barrelmc.barrel.server.ProxyServer;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket;

import java.util.function.Consumer;

public class Live {

    private final HttpClient httpClient = MinecraftAuth.createHttpClient();

    public Thread requestLiveToken(Session session, String username) {
        Thread thread = new Thread(() -> {
            try {
                // Returns once the player has entered the code on the microsoft website
                BedrockAuthManager xboxAccount = BedrockAuthManager.create(this.httpClient, ProxyServer.getInstance().getBedrockPacketCodec().getMinecraftVersion())
                        .login(DeviceCodeMsaAuthService::new, (Consumer<MsaDeviceCode>) deviceCode -> this.sendDeviceCode(session, deviceCode));

                AuthManager.getInstance().getXboxAccounts().put(username, xboxAccount);
                session.send(new ClientboundSystemChatPacket(Component.text("§eSuccessfully authenticated with Xbox Live. Please rejoin!"), false));
            } catch (InterruptedException ignored) {
                // The player left before logging in
            } catch (Exception e) {
                session.disconnect("§cAn error occurred while authenticating to Xbox Live. Please rejoin the server.");
                e.printStackTrace();
            } finally {
                AuthManager.getInstance().getLoginThreads().remove(username, Thread.currentThread());
            }
        });
        thread.start();

        return thread;
    }

    private void sendDeviceCode(Session session, MsaDeviceCode d) {
        Component linkComponent = Component.text(d.getVerificationUri())
                .clickEvent(ClickEvent.openUrl(d.getVerificationUri()))
                .color(NamedTextColor.GREEN)
                .decorate(TextDecoration.UNDERLINED);
        Component codeComponent = Component.text(d.getUserCode())
                .color(NamedTextColor.GREEN)
                .hoverEvent(Component.text("Click to copy code to clipboard."))
                .clickEvent(ClickEvent.copyToClipboard(d.getUserCode()));

        TextComponent textComponent = Component.text()
                .append(Component.text("§eAuthenticate at ").append(linkComponent))
                .append(Component.text(" §eusing the code ").append(codeComponent))
                .append(Component.text(". §eThis code will expire in ")
                        .append(Component.text((d.getExpireTimeMs() - System.currentTimeMillis()) / 1000 + " seconds.")))
                .build();
        session.send(new ClientboundSystemChatPacket(textComponent, false));
    }
}
