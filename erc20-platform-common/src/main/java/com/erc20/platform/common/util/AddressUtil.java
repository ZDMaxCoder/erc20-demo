package com.erc20.platform.common.util;

import java.util.regex.Pattern;

public final class AddressUtil {

    private static final Pattern ETH_ADDRESS_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private AddressUtil() {
    }

    public static boolean isValid(String address) {
        if (address == null || address.isEmpty()) {
            return false;
        }
        return ETH_ADDRESS_PATTERN.matcher(address).matches();
    }

    public static String normalize(String address) {
        return address.toLowerCase();
    }
}
