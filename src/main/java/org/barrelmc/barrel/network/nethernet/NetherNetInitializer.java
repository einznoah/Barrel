package org.barrelmc.barrel.network.nethernet;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import org.cloudburstmc.protocol.bedrock.BedrockClientSession;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;

// Sets up a connection to a server that is reached over nethernet, as the library does it for one over raknet
public abstract class NetherNetInitializer extends ChannelInitializer<Channel> {

    @Override
    protected void initChannel(Channel channel) {
        channel.pipeline()
                .addLast(NetherNetFrameCodec.NAME, new NetherNetFrameCodec())
                .addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3())
                .addLast(BedrockPeer.NAME, new NetherNetPeer(channel, this::createSession));
    }

    private BedrockSession createSession(BedrockPeer peer, int subClientId) {
        BedrockClientSession session = new BedrockClientSession(peer, subClientId);
        this.initSession(session);
        return session;
    }

    protected abstract void initSession(BedrockClientSession session);
}
