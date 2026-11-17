package com.reachlio.api.google;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.reachlio.api.auth.Crypto;

/** Search Console API (저장된 refresh token 으로 호출) */
@Component
public class GoogleApi {

	public record Site(String siteUrl, String permissionLevel) {}

	private record SiteList(List<Site> siteEntry) {}

	private final RestClient http = RestClient.create();
	private final ClientRegistration google;
	private final JdbcClient db;
	private final Crypto crypto;

	GoogleApi(ClientRegistrationRepository registrations, JdbcClient db, Crypto crypto) {
		this.google = registrations.findByRegistrationId("google");
		this.db = db;
		this.crypto = crypto;
	}

	// 소유확인된 속성만 (siteUnverifiedUser 는 실적 조회 불가)
	public List<Site> sites(long userId) {
		var list = http.get().uri("https://www.googleapis.com/webmasters/v3/sites")
			.headers(h -> h.setBearerAuth(accessToken(userId)))
			.retrieve().body(SiteList.class);
		if (list == null || list.siteEntry() == null) {
			return List.of(); // 속성이 하나도 없으면 siteEntry 가 빠져서 온다
		}
		return list.siteEntry().stream().filter(s -> !"siteUnverifiedUser".equals(s.permissionLevel())).toList();
	}

	// 호출마다 토큰 갱신. 매일 수집 호출이 많아지면 만료(1시간) 전까지 캐시
	private String accessToken(long userId) {
		String encrypted = db.sql("SELECT secret_enc FROM credential WHERE user_id = ? AND engine = 'GOOGLE'")
			.param(userId).query(String.class).optional()
			.orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Google 연동이 필요합니다. 다시 로그인해 주세요"));

		var form = new LinkedMultiValueMap<String, String>();
		form.add("grant_type", "refresh_token");
		form.add("refresh_token", crypto.decrypt(encrypted));
		form.add("client_id", google.getClientId());
		form.add("client_secret", google.getClientSecret());
		Map<?, ?> res = http.post().uri(google.getProviderDetails().getTokenUri())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
			.retrieve().body(Map.class);
		return (String) res.get("access_token");
	}
}
