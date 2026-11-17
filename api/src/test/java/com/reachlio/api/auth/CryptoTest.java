package com.reachlio.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.Test;

class CryptoTest {

	private static Crypto newCrypto() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return new Crypto(Base64.getEncoder().encodeToString(key));
	}

	@Test
	void roundTripAndTamperDetection() {
		var crypto = newCrypto();
		String a = crypto.encrypt("1//refresh-token");
		assertEquals("1//refresh-token", crypto.decrypt(a));
		assertNotEquals(a, crypto.encrypt("1//refresh-token")); // IV 가 매번 달라야 함

		byte[] raw = Base64.getDecoder().decode(a);
		raw[raw.length - 1] ^= 1;
		assertThrows(IllegalStateException.class, () -> crypto.decrypt(Base64.getEncoder().encodeToString(raw)));
		assertThrows(IllegalStateException.class, () -> newCrypto().decrypt(a)); // 다른 키
	}

	@Test
	void rejectsWrongKeyLength() {
		assertThrows(IllegalArgumentException.class, () -> new Crypto(Base64.getEncoder().encodeToString(new byte[16])));
	}
}
