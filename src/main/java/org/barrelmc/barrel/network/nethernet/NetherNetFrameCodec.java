package org.barrelmc.barrel.network.nethernet;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import lombok.Setter;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.common.util.VarInts;

import java.util.List;

// What is sent over a nethernet connection: the packets with their length in front and compressed as over raknet,
// but without the byte raknet has in front of them and never encrypted, the connection is that itself
public class NetherNetFrameCodec extends MessageToMessageCodec<ByteBuf, BedrockPacketWrapper> {

    public static final String NAME = "nethernet-frame-codec";

    // The byte in front of what is compressed tells how
    private static final int ZLIB = 0x00;
    private static final int SNAPPY = 0x01;
    private static final int NOT_COMPRESSED = 0xFF;

    // Nothing is compressed before the server has told how it wants it
    @Setter
    private CompressionStrategy compression;

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockPacketWrapper wrapper, List<Object> out) throws Exception {
        ByteBuf packet = ctx.alloc().ioBuffer(wrapper.getPacketBuffer().readableBytes() + 5);
        try {
            VarInts.writeUnsignedInt(packet, wrapper.getPacketBuffer().readableBytes());
            packet.writeBytes(wrapper.getPacketBuffer(), wrapper.getPacketBuffer().readerIndex(), wrapper.getPacketBuffer().readableBytes());
            if (this.compression == null) {
                out.add(packet.retain());
                return;
            }

            BatchCompression batchCompression = this.compression.getDefaultCompression();
            ByteBuf compressed = batchCompression.encode(ctx, packet);
            try {
                ByteBuf message = ctx.alloc().ioBuffer(compressed.readableBytes() + 1);
                message.writeByte(getHeader(batchCompression.getAlgorithm()));
                message.writeBytes(compressed);
                out.add(message);
            } finally {
                compressed.release();
            }
        } finally {
            packet.release();
        }
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf message, List<Object> out) throws Exception {
        ByteBuf packets = this.compression == null ? message.retain() : this.compression.getCompression(getAlgorithm(message.readUnsignedByte())).decode(ctx, message);
        try {
            // A message is one packet or many
            while (packets.isReadable()) {
                out.add(packets.readRetainedSlice(VarInts.readUnsignedInt(packets)));
            }
        } finally {
            packets.release();
        }
    }

    private static int getHeader(CompressionAlgorithm algorithm) {
        if (algorithm == PacketCompressionAlgorithm.ZLIB) {
            return ZLIB;
        } else if (algorithm == PacketCompressionAlgorithm.SNAPPY) {
            return SNAPPY;
        }
        return NOT_COMPRESSED;
    }

    private static CompressionAlgorithm getAlgorithm(int header) {
        switch (header) {
            case ZLIB:
                return PacketCompressionAlgorithm.ZLIB;
            case SNAPPY:
                return PacketCompressionAlgorithm.SNAPPY;
            case NOT_COMPRESSED:
                return PacketCompressionAlgorithm.NONE;
            default:
                throw new IllegalStateException("A message is compressed in a way that is not known: " + header);
        }
    }
}
