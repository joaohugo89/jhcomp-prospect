package com.jhcomp.prospect.identity.application;

import java.security.SecureRandom;

/**
 * Senha temporaria para membro novo ou redefinido (D28).
 *
 * <p>20 caracteres de um alfabeto sem simbolos ambiguos (sem 0/O, 1/l/I),
 * cerca de 113 bits: o administrador a dita ou copia para o membro, que a
 * troca no primeiro acesso.
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
