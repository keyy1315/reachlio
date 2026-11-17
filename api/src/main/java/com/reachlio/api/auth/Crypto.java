package com.reachlio.api.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** credential.secret_enc 용 AES-256-GCM. 저장 형식: base64(iv 12바이트 + 암호문·태그) */
@Component
public class Crypto {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;

	private final SecretKey key;
	private final SecureRandom random = new SecureRandom();

	public Crypto(@Value("${app.credential-key}") String base64Key) {
		byte[] raw = Base64.getDecoder().decode(base64Key);
		if (raw.length != 32) {
			throw new IllegalArgumentException("app.credential-key 는 base64 로 인코딩한 32바이트여야 합니다");
		}
		key = new SecretKeySpec(raw, "AES");
	}

	public String encrypt(String plain) {
		byte[] iv = new byte[IV_BYTES];
		random.nextBytes(iv);
		byte[] enc = cipher(Cipher.ENCRYPT_MODE, iv, plain.getBytes(StandardCharsets.UTF_8));
		return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
	}

	public String decrypt(String stored) {
		byte[] all = Base64.getDecoder().decode(stored);
		byte[] iv = java.util.Arrays.copyOf(all, IV_BYTES);
		byte[] enc = java.util.Arrays.copyOfRange(all, IV_BYTES, all.length);
		return new String(cipher(Cipher.DECRYPT_MODE, iv, enc), StandardCharsets.UTF_8);
	}

	private byte[] cipher(int mode, byte[] iv, byte[] input) {
		try {
			var c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
			return c.doFinal(input);
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("credential 암복호화 실패", e);
		}
	}
}
