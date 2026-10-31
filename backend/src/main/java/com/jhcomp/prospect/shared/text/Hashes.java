package com.jhcomp.prospect.shared.text;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Resumos criptograficos usados para comparar conteudo, nunca para senha. */
public final class Hashes {

    private Hashes() {
    }

    /** SHA-256 em hexadecimal minusculo. */
    public static String sha256Hex(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every JVM", exception);
        }
    }
}
