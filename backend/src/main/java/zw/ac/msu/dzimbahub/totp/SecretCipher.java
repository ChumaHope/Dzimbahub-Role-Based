package zw.ac.msu.dzimbahub.totp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts authenticator secrets before they are saved (AES-256-GCM), so a database leak alone
 * does not reveal them. The key comes from TOTP_ENCRYPTION_KEY, or from APP_JWT_SECRET if that is not set.
 * Changing the key means every user has to set up their authenticator again.
 */
@Component
public class SecretCipher {
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${app.totp.encryption-key}") String keyText) {
        if (keyText == null || keyText.length() < 16)
            throw new IllegalStateException("TOTP_ENCRYPTION_KEY (or APP_JWT_SECRET) must be at least 16 characters.");
        try {
            byte[] k = MessageDigest.getInstance("SHA-256").digest(("dzimbahub-totp:" + keyText).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(k, "AES");
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) { throw new IllegalStateException("Could not encrypt the authenticator secret", e); }
    }

    public String decrypt(String stored) {
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Could not read the authenticator secret (was the key changed?)", e); }
    }
}
