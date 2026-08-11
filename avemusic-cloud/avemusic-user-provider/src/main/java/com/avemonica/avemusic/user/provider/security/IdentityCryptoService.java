package com.avemonica.avemusic.user.provider.security;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Component
public final class IdentityCryptoService {

    private static final String AES_ALGORITHM =
            "AES/GCM/NoPadding";

    private static final String HMAC_ALGORITHM =
            "HmacSHA256";

    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom =
            new SecureRandom();

    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;
    private final int keyVersion;

    public IdentityCryptoService(
            IdentityCryptoProperties properties
    ) {
        this.aesKey = new SecretKeySpec(
                decode(properties.aesKeyBase64()),
                "AES"
        );

        this.hmacKey = new SecretKeySpec(
                decode(properties.hmacKeyBase64()),
                HMAC_ALGORITHM
        );

        this.keyVersion = properties.keyVersion();
    }

    public String encrypt(
            long userId,
            String fieldName,
            String plaintext
    ) {
        if (plaintext == null
                || plaintext.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + "不能为空"
            );
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher =
                    Cipher.getInstance(
                            AES_ALGORITHM
                    );

            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            cipher.updateAAD(
                    aad(
                            userId,
                            fieldName,
                            keyVersion
                    )
            );

            byte[] encrypted =
                    cipher.doFinal(
                            plaintext.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            byte[] payload =
                    ByteBuffer
                            .allocate(
                                    iv.length
                                            + encrypted.length
                            )
                            .put(iv)
                            .put(encrypted)
                            .array();

            return Base64
                    .getEncoder()
                    .encodeToString(payload);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "实名信息加密失败",
                    exception
            );
        }
    }

    public String decrypt(
            long userId,
            String fieldName,
            int storedKeyVersion,
            String ciphertext
    ) {
        try {
            byte[] payload =
                    Base64
                            .getDecoder()
                            .decode(ciphertext);

            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException(
                        "密文格式错误"
                );
            }

            byte[] iv = Arrays.copyOfRange(
                    payload,
                    0,
                    IV_LENGTH
            );

            byte[] encrypted =
                    Arrays.copyOfRange(
                            payload,
                            IV_LENGTH,
                            payload.length
                    );

            Cipher cipher =
                    Cipher.getInstance(
                            AES_ALGORITHM
                    );

            cipher.init(
                    Cipher.DECRYPT_MODE,
                    aesKey,
                    new GCMParameterSpec(
                            TAG_LENGTH_BITS,
                            iv
                    )
            );

            cipher.updateAAD(
                    aad(
                            userId,
                            fieldName,
                            storedKeyVersion
                    )
            );

            byte[] plaintext =
                    cipher.doFinal(encrypted);

            return new String(
                    plaintext,
                    StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException
                 | IllegalArgumentException exception) {

            throw new IllegalStateException(
                    "实名信息解密失败",
                    exception
            );
        }
    }

    public String documentNumberFingerprint(
            String documentNumber
    ) {
        String normalized =
                documentNumber
                        .replaceAll("\\s+", "")
                        .toUpperCase(Locale.ROOT);

        try {
            Mac mac =
                    Mac.getInstance(
                            HMAC_ALGORITHM
                    );

            mac.init(hmacKey);

            byte[] result =
                    mac.doFinal(
                            normalized.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat
                    .of()
                    .formatHex(result);

        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "证件号指纹生成失败",
                    exception
            );
        }
    }

    public int keyVersion() {
        return keyVersion;
    }

    private static byte[] aad(
            long userId,
            String fieldName,
            int version
    ) {
        String value =
                userId
                        + ":"
                        + fieldName
                        + ":"
                        + version;

        return value.getBytes(
                StandardCharsets.UTF_8
        );
    }

    private static byte[] decode(
            String value
    ) {
        return Base64
                .getDecoder()
                .decode(value);
    }
}