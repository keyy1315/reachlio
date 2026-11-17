package com.reachlio.api.site;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.reachlio.api.auth.Users;
import com.reachlio.api.google.GoogleApi;
import com.reachlio.api.google.GoogleCollector;

@RestController
class SiteController {

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	static class ConnectGoogle {
		private String property;
	}

	private final JdbcClient db;
	private final Users users;
	private final GoogleApi google;
	private final GoogleCollector collector;

	SiteController(JdbcClient db, Users users, GoogleApi google, GoogleCollector collector) {
		this.db = db;
		this.users = users;
		this.google = google;
		this.collector = collector;
	}

	/** 연결 후보: 사용자 Search Console 속성 목록 */
	@GetMapping("/api/google/sites")
	List<GoogleApi.Site> googleSites(@AuthenticationPrincipal OidcUser user) {
		return google.sites(users.id(user));
	}

	/** 내 사이트와 엔진 연결 (사이트당 엔진별 1행) */
	@GetMapping("/api/sites")
	List<Map<String, Object>> sites(@AuthenticationPrincipal OidcUser user) {
		return db.sql("""
				SELECT s.id, s.domain, c.engine, c.property
				FROM site s LEFT JOIN connection c ON c.site_id = s.id
				WHERE s.user_id = ? ORDER BY s.domain, c.engine""")
			.param(users.id(user)).query().listOfRows();
	}

	/** Search Console 속성을 골라 사이트로 등록 + GOOGLE 연결 */
	// 트랜잭션 없음: 두 INSERT 모두 upsert 라 다시 보내면 복구되고, 커밋 전에 백필 스레드가 연결을 못 찾는 문제를 피함
	@PostMapping("/api/sites/google")
	Map<String, Object> connectGoogle(@AuthenticationPrincipal OidcUser user, @RequestBody ConnectGoogle body) {
		long userId = users.id(user);
		// 클라이언트가 보낸 값은 믿지 않고, 실제 내 Search Console 속성인지 확인
		boolean mine = google.sites(userId).stream().anyMatch(s -> s.getSiteUrl().equals(body.getProperty()));
		if (!mine) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "내 Search Console 에 없는 속성입니다");
		}
		String domain = domainOf(body.getProperty());
		long siteId = db.sql("""
				INSERT INTO site (user_id, domain) VALUES (?, ?)
				ON CONFLICT (user_id, domain) DO UPDATE SET domain = EXCLUDED.domain
				RETURNING id""")
			.params(userId, domain).query(Long.class).single();
		long connectionId = db.sql("""
				INSERT INTO connection (site_id, engine, property) VALUES (?, 'GOOGLE', ?)
				ON CONFLICT (site_id, engine) DO UPDATE SET property = EXCLUDED.property
				RETURNING id""")
			.params(siteId, body.getProperty()).query(Long.class).single();
		collector.backfill(connectionId); // 백그라운드, 진행 상황은 collect_run
		return Map.of("siteId", siteId, "domain", domain);
	}

	/**
	 * 엔진 간 같은 사이트로 묶는 기준 주소. www. 는 뗀다 (엔진별 실제 등록 주소는 connection.property 에 원본 유지)
	 * sc-domain:example.com → example.com, https://www.example.com/ → example.com
	 */
	static String domainOf(String property) {
		String host = property.startsWith("sc-domain:") ? property.substring("sc-domain:".length())
				: URI.create(property).getHost();
		host = host.toLowerCase(Locale.ROOT);
		return host.startsWith("www.") ? host.substring("www.".length()) : host;
	}
}
