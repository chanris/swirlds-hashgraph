package com.cystrix.util;

import java.util.UUID;

public class UUIDUtils {
    public static String geneUUIDWithoutDash() {
        UUID uuid = UUID.randomUUID();
        String uuidStr = uuid.toString().replaceAll("-", "");
        return uuidStr;
    }
}
