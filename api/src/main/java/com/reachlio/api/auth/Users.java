package com.reachlio.api.auth;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class Users {

	private final JdbcClient db;

	Users(JdbcClient db) {
		this.db = db;
	}

	/** 로그인 사용자의 users.id (로그인 성공 시 upsert 되므로 항상 있음) */
	public long id(OidcUser user) {
		return db.sql("SELECT id FROM users WHERE google_sub = ?").param(user.getSubject()).query(Long.class).single();
	}
}
