package com.game.auth.security;

import com.game.auth.config.AuthProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
        return parseIpLiteral(candidate).equals(remoteAddress);
    }

    private String singleForwardedAddress(String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.contains(",")) {
            return null;
        }
        String normalized = parseIpLiteral(forwardedFor.trim());
        return normalized.isBlank() ? null : normalized;
    }

    private boolean cidrMatches(String cidr, String remoteAddress) {
        String[] parts = cidr.split("/", -1);
        if (parts.length != 2) {
            return false;
        }
        try {
            byte[] network = parseIpBytes(parts[0]);
            byte[] address = parseIpBytes(remoteAddress);
            if (network.length == 0 || address.length == 0) {
                return false;
            }
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
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String normalizeAddress(String value) {
        return parseIpLiteral(value);
    }

    private String parseIpLiteral(String value) {
        byte[] bytes = parseIpBytes(value);
        if (bytes.length == 0) {
            return "";
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException exception) {
            return "";
        }
    }

    private byte[] parseIpBytes(String value) {
        if (value == null || value.isBlank() || containsUnsafeCharacters(value)) {
            return new byte[0];
        }
        String candidate = value.trim();
        if (candidate.contains(",") || candidate.contains("[") || candidate.contains("]")) {
            return new byte[0];
        }
        if (candidate.indexOf(':') >= 0) {
            return parseIpv6(candidate);
        }
        return parseIpv4(candidate);
    }

    private boolean containsUnsafeCharacters(String value) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isISOControl(current) || Character.isWhitespace(current)) {
                return true;
            }
        }
        return false;
    }

    private byte[] parseIpv4(String candidate) {
        String[] parts = candidate.split("\\.", -1);
        if (parts.length != 4) {
            return new byte[0];
        }
        byte[] bytes = new byte[4];
        for (int i = 0; i < parts.length; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) {
                return new byte[0];
            }
            int octet = Integer.parseInt(parts[i]);
            if (octet > 255) {
                return new byte[0];
            }
            bytes[i] = (byte) octet;
        }
        return bytes;
    }

    private byte[] parseIpv6(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (!lower.matches("[0-9a-f:.]+") || !lower.contains(":")) {
            return new byte[0];
        }
        try {
            InetAddress address = InetAddress.getByName(lower);
            byte[] bytes = address.getAddress();
            return bytes.length == 16 ? Arrays.copyOf(bytes, bytes.length) : new byte[0];
        } catch (UnknownHostException exception) {
            return new byte[0];
        }
    }
}
