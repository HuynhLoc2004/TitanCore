package com.game.auth.security;

import com.game.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

@Component
public class ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final AuthProperties authProperties;

    public ClientIpResolver(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalizeAddress(request.getRemoteAddr());
        if (!authProperties.trustedProxy().enabled() || !isTrustedProxy(remoteAddress)) {
            return remoteAddress;
        }
        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        String forwardedAddress = singleForwardedAddress(forwardedFor);
        return forwardedAddress == null ? remoteAddress : forwardedAddress;
    }

    private boolean isTrustedProxy(String remoteAddress) {
        List<String> allowedProxies = authProperties.trustedProxy().allowedProxies();
        if (allowedProxies == null || allowedProxies.isEmpty()) {
            return false;
        }
        for (String allowedProxy : allowedProxies) {
            if (matches(allowedProxy, remoteAddress)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String allowedProxy, String remoteAddress) {
        if (allowedProxy == null || allowedProxy.isBlank()) {
            return false;
        }
        String candidate = allowedProxy.trim();
        if (candidate.contains("/")) {
            return cidrMatches(candidate, remoteAddress);
        }
        return normalizeAddress(candidate).equals(remoteAddress);
    }

    private String singleForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.contains(",")) {
            return null;
        }
        String normalized = normalizeAddress(forwardedFor.trim());
        return normalized.isBlank() ? null : normalized;
    }

    private boolean cidrMatches(String cidr, String remoteAddress) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            return false;
        }
        try {
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            byte[] address = InetAddress.getByName(remoteAddress).getAddress();
            if (network.length != address.length) {
                return false;
            }
            int prefixLength = Integer.parseInt(parts[1]);
            if (prefixLength < 0 || prefixLength > network.length * Byte.SIZE) {
                return false;
            }
            int fullBytes = prefixLength / Byte.SIZE;
            int remainingBits = prefixLength % Byte.SIZE;
            for (int i = 0; i < fullBytes; i++) {
                if (network[i] != address[i]) {
                    return false;
                }
            }
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (Byte.SIZE - remainingBits);
            return (network[fullBytes] & mask) == (address[fullBytes] & mask);
        } catch (IllegalArgumentException | UnknownHostException exception) {
            return false;
        }
    }

    private String normalizeAddress(String value) {
        try {
            return InetAddress.getByName(value).getHostAddress();
        } catch (UnknownHostException | RuntimeException exception) {
            return "";
        }
    }
}
