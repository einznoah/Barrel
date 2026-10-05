/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network;

import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;

public class JavaPacketHandler extends SessionAdapter {

    private Player player = null;

    @Override
    public void packetSent(Session session, Packet packet) {
        //System.out.println("Sent Java " + packet.toString());
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        //System.out.println("Received Java " + packet.toString());
        if (this.player == null) {
            if (packet instanceof ServerboundHelloPacket) {
                ServerboundHelloPacket loginPacket = (ServerboundHelloPacket) packet;

                if (ProxyServer.getInstance().getConfig().getAuth().equals("offline") || AuthManager.getInstance().getXboxAccounts().containsKey(loginPacket.getUsername())) {
                    Player oldPlayer = ProxyServer.getInstance().getPlayerByName(loginPacket.getUsername());
                    if (oldPlayer != null) {
                        oldPlayer.disconnect("You logged in from another location");
                    }

                    this.player = new Player(loginPacket, session);
                }
            }
        } else {
            player.getPacketTranslatorManager().translate((MinecraftPacket) packet);
        }
    }
}
