package com.jobtracker.secret;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Locks small secrets (such as an app password) before they go into the database. The key is made once and kept
 * in a file next to the data, so the database file alone is not enough to read them.
 */
@Component
public class SecretBox {

    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretBox(@Value("${jobtracker.secret.key-file:data/secret.key}") Path keyFile) {
        this.key = new SecretKeySpec(loadOrCreate(keyFile), "AES");
    }

    public String lock(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not lock the secret", e);
        }
    }

    public String unlock(String locked) {
        try {
            byte[] in = Base64.getDecoder().decode(locked);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, in, 0, IV_BYTES));
            return new String(c.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Could not unlock the secret (was the key file changed?)", e);
        }
    }

    private static byte[] loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) return Base64.getDecoder().decode(Files.readString(file).strip());
            byte[] k = new byte[32];
            RANDOM.nextBytes(k);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, Base64.getEncoder().encodeToString(k));
            return k;
        } catch (IOException e) {
            throw new IllegalStateException("Could not read or create the key file " + file, e);
        }
    }
}
