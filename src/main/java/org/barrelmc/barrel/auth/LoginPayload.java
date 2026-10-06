package org.barrelmc.barrel.auth;

import org.cloudburstmc.protocol.bedrock.data.auth.AuthPayload;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;

import java.util.List;

// What a bedrock client tells a server about who it is: the token of its account, and the chain of certificates
// servers went by before there were tokens. A server does not take a login that has only one of them
public record LoginPayload(AuthType authType, List<String> chain, String token) implements AuthPayload {

    @Override
    public AuthType getAuthType() {
        return this.authType;
    }
}
