package ru.itam.typing.realistic;

/**
 * Conversion helper for the KSC Open API host field {@code KLHST_WKS_IP_LONG}.
 * Kaspersky documents that field as a paramLong containing IPv4 bytes in little-endian order.
 *
 * This helper is deliberately isolated from classification logic: it models source encoding only.
 */
public final class KscIpv4Long {
    private static final long MAX_IPV4 = 0xffff_ffffL;

    private KscIpv4Long() {}

    public static long encodeDocumentedLittleEndian(String ipv4) {
        int[] octets = parseIpv4(ipv4);
        return (octets[0] & 0xffL)
                | ((octets[1] & 0xffL) << 8)
                | ((octets[2] & 0xffL) << 16)
                | ((octets[3] & 0xffL) << 24);
    }

    public static String decodeDocumentedLittleEndian(long value) {
        if (value < 0 || value > MAX_IPV4) {
            throw new IllegalArgumentException("KSC IPv4 long must be an unsigned 32-bit value: " + value);
        }
        return (value & 0xffL) + "."
                + ((value >>> 8) & 0xffL) + "."
                + ((value >>> 16) & 0xffL) + "."
                + ((value >>> 24) & 0xffL);
    }

    private static int[] parseIpv4(String ipv4) {
        if (ipv4 == null) throw new IllegalArgumentException("IPv4 must not be null");
        String[] parts = ipv4.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Invalid IPv4 address: " + ipv4);
        int[] octets = new int[4];
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty() || !parts[i].chars().allMatch(Character::isDigit)) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ipv4);
            }
            try {
                octets[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ipv4, e);
            }
            if (octets[i] < 0 || octets[i] > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address: " + ipv4);
            }
        }
        return octets;
    }
}
