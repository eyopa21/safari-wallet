package org.example.service;

import java.util.concurrent.ThreadLocalRandom;

final class Ids {

    private Ids() {
    }

    static String next(String prefix) {
        return prefix + "-" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x100000, 0xFFFFFF));
    }
}
