package org.barrelmc.barrel.auth;

import com.alibaba.fastjson2.JSONObject;
import org.cloudburstmc.protocol.bedrock.codec.v818.serializer.LoginSerializer_v818;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;

// The serializer of the library writes a login with a token or with certificates, a client sends both
public class LoginSerializer extends LoginSerializer_v818 {

    @Override
    protected String writeAuthJwt(AuthPayload payload) {
        if (!(payload instanceof LoginPayload)) {
            return super.writeAuthJwt(payload);
        }

        LoginPayload loginPayload = (LoginPayload) payload;
        JSONObject certificate = new JSONObject();
        certificate.put("chain", loginPayload.chain());

        JSONObject connectionRequest = new JSONObject();
        // Counted from 0, the first type of the library is for a type that is not known
        connectionRequest.put("AuthenticationType", loginPayload.authType().ordinal() - 1);
        // With the line break the json writer of the game ends what it writes with
        connectionRequest.put("Certificate", certificate.toJSONString() + "\n");
        connectionRequest.put("Token", loginPayload.token());
        return connectionRequest.toJSONString();
    }
}
