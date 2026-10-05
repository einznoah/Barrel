package org.barrelmc.barrel.network.translator.bedrock;

import com.alibaba.fastjson.JSONObject;
import org.barrelmc.barrel.network.translator.interfaces.BedrockPacketTranslator;
import org.barrelmc.barrel.player.Player;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientToServerHandshakePacket;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;

import javax.crypto.SecretKey;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;

public class ServerToClientHandshakePacket implements BedrockPacketTranslator {

    @Override
    public boolean immediate() {
        return true;
    }

    @Override
    public void translate(BedrockPacket pk, Player player) {
        try {
            String[] saltJwt = ((org.cloudburstmc.protocol.bedrock.packet.ServerToClientHandshakePacket) pk).getJwt().split("\\.");
            JSONObject header = JSONObject.parseObject(new String(Base64.getUrlDecoder().decode(saltJwt[0])));
            JSONObject payload = JSONObject.parseObject(new String(Base64.getUrlDecoder().decode(saltJwt[1])));
            ECPublicKey serverKey = EncryptionUtils.parseKey(header.getString("x5u"));
            SecretKey key = EncryptionUtils.getSecretKey(
                    player.getPrivateKey(),
                    serverKey,
                    Base64.getDecoder().decode(payload.getString("salt"))
            );
            player.getBedrockSession().enableEncryption(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ClientToServerHandshakePacket clientToServerHandshake = new ClientToServerHandshakePacket();
        player.getBedrockSession().sendPacketImmediately(clientToServerHandshake);
    }
}
