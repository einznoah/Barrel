package org.barrelmc.barrel.network.nethernet;

import org.cloudburstmc.netty.channel.nethernet.signaling.HttpSignalingSettings;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// The game first asks a server whether it is one of nethernet, and so whether it answers to https or to http. A
// server says what it shows in the list of the servers around a player, and one that is set not to show itself
// there answers with nothing: that it answers is all that is asked for here
public class NetherNetProbe {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

    public static HttpSignalingSettings.Scheme probe(InetSocketAddress address) throws Exception {
        int code;
        try {
            code = get(address, true);
        } catch (SSLException e) {
            // Something answered and it was not https, which is what http is tried for
            return answered(address, get(address, false), HttpSignalingSettings.Scheme.HTTP);
        } catch (Exception e) {
            throw new ConnectException(address + " could not be reached: " + e);
        }
        return answered(address, code, HttpSignalingSettings.Scheme.HTTPS);
    }

    private static int get(InetSocketAddress address, boolean secure) throws Exception {
        String host = address.getHostString();
        // An address of ipv6 is written in brackets
        URI uri = URI.create((secure ? "https" : "http") + "://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + address.getPort() + "/v1/join");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(TIMEOUT).header("Accept", "application/json").GET().build();
        return HTTP.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private static HttpSignalingSettings.Scheme answered(InetSocketAddress address, int code, HttpSignalingSettings.Scheme scheme) throws ConnectException {
        if (code / 100 == 2) {
            return scheme;
        }
        throw new ConnectException(code == 404 ? address + " does not serve NetherNet (HTTP 404)" : code == 426 ? address + " requires TLS" : address + " answered HTTP " + code + " to the probe");
    }
}
