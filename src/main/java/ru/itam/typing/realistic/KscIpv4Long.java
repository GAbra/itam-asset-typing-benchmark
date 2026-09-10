package ru.itam.typing.realistic;

/**
 * Conversion helper for the numeric IPv4 value used by KSC Open API host fields.
 * The research contract stores the dotted address as one unsigned 32-bit value in network-octet order.
 */
public final class KscIpv4Long {
    private static final long MAX_IPV4 = 0xffff_ffffL;

    private KscIpv4Long() {}

    public static long encodeKscParamLong(String ipv4) {
        int[] o = parseIpv4(ipv4);
        return ((o[0] & 0xffL) << 24)
                | ((o[1] & 0xffL) << 16)
                | ((o[2] & 0xffL) << 8)
                | (o[3] & 0xffL);
    }

    public static String decodeKscParamLong(long value) {
        if (value < 0 || value > MAX_IPV4) {
            throw new IllegalArgumentException("KSC IPv4 long must be an unsigned 32-bit value: " + value);
        }
        return ((value >>> 24) & 0xffL) + "."
                + ((value >>> 16) & 0xffL) + "."
                + ((value >>> 8) & 0xffL) + "."
                + (value & 0xffL);
    }

    /** Compatibility alias retained for already-written research code. */
    public static long encodeDocumentedLittleEndian(String ipv4) {
        return encodeKscParamLong(ipv4);
    }

    /** Compatibility alias retained for already-written research code. */
    public static String decodeDocumentedLittleEndian(long value) {
        return decodeKscParamLong(value);
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
