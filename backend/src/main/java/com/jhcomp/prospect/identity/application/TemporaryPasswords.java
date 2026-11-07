package com.jhcomp.prospect.identity.application;

import java.security.SecureRandom;

/**
 * A temporary password for a new or reset member (D28).
 *
 * <p>20 characters from an alphabet without ambiguous symbols (no 0/O, 1/l/I),
 * around 113 bits: the administrator reads or copies it to the member, who
 * changes it on first access.
 */
final class TemporaryPasswords {

    static final int LENGTH = 20;

    private static final char[] ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private TemporaryPasswords() {
    }

    static String generate() {
        char[] password = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            password[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }
        return new String(password);
    }
}
