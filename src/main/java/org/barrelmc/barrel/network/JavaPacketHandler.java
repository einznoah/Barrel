/*
 * Copyright (c) 2021 BarrelMC Team
 * This project is licensed under the MIT License
 */

package org.barrelmc.barrel.network;

import org.barrelmc.barrel.auth.AuthManager;
import org.barrelmc.barrel.player.Player;
import org.barrelmc.barrel.server.ProxyServer;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftPacket;

public class JavaPacketHandler extends SessionAdapter {

    private Player player = null;
    // Whether it was looked at who has joined
    private boolean loggedIn = false;

    @Override
    public void packetSent(Session session, Packet packet) {
        //System.out.println("Sent Java " + packet.toString());
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        //System.out.println("Received Java " + packet.toString());
        if (this.player == null) {
            // The name a client sends first is only what it says. Who it is the library tells once the client was
            // let in, which is after mojang was asked when java accounts are checked. Nothing is done for a name
            // before that: nobody is sent away for it and no xbox account is looked up
            GameProfile profile = this.loggedIn ? null : session.getFlag(MinecraftConstants.PROFILE_KEY);
            if (profile != null) {
                this.loggedIn = true;

                if (ProxyServer.getInstance().getConfig().getAuth().equals("offline") || AuthManager.getInstance().hasXboxAccount(profile)) {
                    Player oldPlayer = ProxyServer.getInstance().getPlayerByName(profile.getName());
                    if (oldPlayer != null) {
                        oldPlayer.disconnect("You logged in from another location");
                    }

                    this.player = new Player(profile, session);
                }
            }
        } else {
            player.getPacketTranslatorManager().translate((MinecraftPacket) packet);
        }
    }
}
