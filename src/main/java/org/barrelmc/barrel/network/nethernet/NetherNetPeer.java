package org.barrelmc.barrel.network.nethernet;

import io.netty.channel.Channel;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SnappyCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.common.util.Zlib;

import javax.crypto.SecretKey;

// The connection to a server that is reached over nethernet. The library does to its connections what a
// connection over raknet needs, this is what is not the same
public class NetherNetPeer extends BedrockPeer {

    private CompressionStrategy compressionStrategy;

    public NetherNetPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
    }

    @Override
    public void enableEncryption(SecretKey secretKey) {
        // A nethernet connection is encrypted itself, the packets are not encrypted once more
    }

    @Override
    public void setCompression(PacketCompressionAlgorithm algorithm) {
        switch (algorithm) {
            case ZLIB:
                this.setCompression(new SimpleCompressionStrategy(new ZlibCompression(Zlib.RAW)));
                break;
            case SNAPPY:
                this.setCompression(new SimpleCompressionStrategy(new SnappyCompression()));
                break;
            default:
                this.setCompression(new SimpleCompressionStrategy(new NoopCompression()));
                break;
        }
    }

    @Override
    public void setCompression(CompressionStrategy strategy) {
        this.compressionStrategy = strategy;
        ((NetherNetFrameCodec) this.channel.pipeline().get(NetherNetFrameCodec.NAME)).setCompression(strategy);
    }

    @Override
    public CompressionStrategy getCompressionStrategy() {
        return this.compressionStrategy;
    }

    @Override
    public int getRakVersion() {
        // The channel is not one of raknet that could be asked
        return this.getCodec().getRaknetProtocolVersion();
    }
}
