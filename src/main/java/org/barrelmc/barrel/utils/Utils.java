package org.barrelmc.barrel.utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.barrelmc.barrel.network.converter.BlockConverter;
import org.barrelmc.barrel.server.ProxyServer;
import org.geysermc.mcprotocollib.protocol.codec.MinecraftTypes;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;

import java.security.SignatureException;

public class Utils {

    public static String lengthCutter(String bedrockName, int length) {
        if (bedrockName == null) {
            return "null";
        }

        if (bedrockName.length() > length) {
            return bedrockName.substring(0, length);
        } else {
            return bedrockName;
        }
    }

    public static ChunkSection createChunkSection() {
        ProxyServer proxyServer = ProxyServer.getInstance();
        return new ChunkSection(0, BlockConverter.getJavaBlockStateCount(), proxyServer.getDefaultBiomeId(), proxyServer.getBiomeCount());
    }

    public static ChunkSection[] createChunkSections() {
        return createChunkSections(ProxyServer.getInstance().getOverworldSectionCount());
    }

    public static ChunkSection[] createChunkSections(int count) {
        ChunkSection[] chunkSections = new ChunkSection[count];
        for (int i = 0; i < chunkSections.length; i++) {
            chunkSections[i] = createChunkSection();
        }

        return chunkSections;
    }

    public static byte[] writeChunkSections(ChunkSection[] chunkSections) {
        ByteBuf byteBuf = Unpooled.buffer();
        for (ChunkSection chunkSection : chunkSections) {
            // The block count kept by ChunkSection#setBlock is off once its palette has been resized
            int blockCount = 0;
            int fluidCount = 0;
            for (int i = 0; i < 4096; i++) {
                int javaStateId = chunkSection.getBlock(i & 15, i >> 8, i >> 4 & 15);
                if (javaStateId != 0) {
                    blockCount++;
                }
                if (BlockConverter.isJavaFluid(javaStateId)) {
                    fluidCount++;
                }
            }

            MinecraftTypes.writeChunkSection(byteBuf, new ChunkSection(blockCount, fluidCount, chunkSection.getBlockData(), chunkSection.getBiomeData()));
        }

        byte[] chunkData = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(chunkData);
        byteBuf.release();
        return chunkData;
    }

    public static byte[] DERToJOSE(byte[] derSignature, Utils.AlgorithmType algorithmType) throws SignatureException {
        // DER Structure: http://crypto.stackexchange.com/a/1797
        boolean derEncoded = derSignature[0] == 0x30 && derSignature.length != algorithmType.ecNumberSize * 2;
        if (!derEncoded) {
            throw new SignatureException("Invalid DER signature format.");
        }

        final byte[] joseSignature = new byte[algorithmType.ecNumberSize * 2];

        //Skip 0x30
        int offset = 1;
        if (derSignature[1] == (byte) 0x81) {
            //Skip sign
            offset++;
        }

        //Convert to unsigned. Should match DER length - offset
        int encodedLength = derSignature[offset++] & 0xff;
        if (encodedLength != derSignature.length - offset) {
            throw new SignatureException("Invalid DER signature format.");
        }

        //Skip 0x02
        offset++;

        //Obtain R number length (Includes padding) and skip it
        int rLength = derSignature[offset++];
        if (rLength > algorithmType.ecNumberSize + 1) {
            throw new SignatureException("Invalid DER signature format.");
        }
        int rPadding = algorithmType.ecNumberSize - rLength;
        //Retrieve R number
        System.arraycopy(derSignature, offset + Math.max(-rPadding, 0), joseSignature, Math.max(rPadding, 0), rLength + Math.min(rPadding, 0));

        //Skip R number and 0x02
        offset += rLength + 1;

        //Obtain S number length. (Includes padding)
        int sLength = derSignature[offset++];
        if (sLength > algorithmType.ecNumberSize + 1) {
            throw new SignatureException("Invalid DER signature format.");
        }
        int sPadding = algorithmType.ecNumberSize - sLength;
        //Retrieve R number
        System.arraycopy(derSignature, offset + Math.max(-sPadding, 0), joseSignature, algorithmType.ecNumberSize + Math.max(sPadding, 0), sLength + Math.min(sPadding, 0));

        return joseSignature;
    }

    public enum AlgorithmType {
        ECDSA256(32), ECDSA384(48);

        public int ecNumberSize;

        AlgorithmType(int ecNumberSize) {
            this.ecNumberSize = ecNumberSize;
        }
    }
}
