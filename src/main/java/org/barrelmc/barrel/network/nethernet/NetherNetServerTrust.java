package org.barrelmc.barrel.network.nethernet;

import org.cloudburstmc.netty.util.nethernet.Identity;
import org.cloudburstmc.netty.util.nethernet.IdentityUtils;
import org.cloudburstmc.netty.util.nethernet.TokenTrust;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;

import java.security.PublicKey;
import java.util.Map;

// Whether the proxy takes who a server says it is when it answers. A server makes its token itself: it tells a key
// and is signed with it, and the library then looks whether the connection is one of that key. The proxy joins the
// server it was told to join, so it does not ask whose key it is, as the game does when it sees a key for the first
// time
public class NetherNetServerTrust implements TokenTrust {

    public static final NetherNetServerTrust INSTANCE = new NetherNetServerTrust();

    private static final String KEY_CLAIM = "cpk";

    // The key is in the token itself, so it is read before anything can be looked at
    private static final JwtConsumer NOT_CHECKED = new JwtConsumerBuilder()
            .setDisableRequireSignature()
            .setSkipSignatureVerification()
            .setSkipAllValidators()
            .build();

    @Override
    public JwtClaims claims(Identity identity) throws Exception {
        String token = identity.assertion().token();
        PublicKey key = readKey(NOT_CHECKED.processToClaims(token).getClaimValue(KEY_CLAIM));

        // A token does not have to tell until when it is good, but one that does must not be over
        JwtClaims claims = new JwtConsumerBuilder()
                .setVerificationKey(key)
                .setAllowedClockSkewInSeconds(60)
                .setSkipDefaultAudienceValidation()
                .build()
                .processToClaims(token);
        // The library reads the key as the text of it, whichever way the server has told it
        claims.setStringClaim(KEY_CLAIM, IdentityUtils.encodePublicKey(key));
        return claims;
    }

    // Mojang says the key is told as a json web key, the servers that are made with the library tell it as the
    // tokens of players do
    @SuppressWarnings("unchecked")
    private static PublicKey readKey(Object key) throws Exception {
        if (key instanceof Map) {
            return PublicJsonWebKey.Factory.newPublicJwk((Map<String, Object>) key).getPublicKey();
        } else if (key instanceof String) {
            return IdentityUtils.decodePublicKey((String) key);
        }
        throw new JoseException("The token of the server does not tell a key");
    }
}
