package com.reachlio.api.auth;

import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController {

	private final JdbcClient db;

	MeController(JdbcClient db) {
		this.db = db;
	}

	@GetMapping("/api/me")
	Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
		return db.sql("""
				SELECT u.email, u.name,
				       EXISTS (SELECT 1 FROM credential c WHERE c.user_id = u.id AND c.engine = 'GOOGLE') AS google_connected
				FROM users u WHERE u.google_sub = ?""")
			.param(user.getSubject())
			.query().singleRow();
	}
}
