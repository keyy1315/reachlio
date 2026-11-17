package com.reachlio.api.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
class AuthConfig {

	@Bean
	SecurityFilterChain security(HttpSecurity http, ClientRegistrationRepository clients,
			OAuth2AuthorizedClientRepository authorizedClients, JdbcClient db, Crypto crypto) throws Exception {
		var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
		// Google은 access_type=offline + prompt=consent 일 때만 refresh token 을 준다 (매일 자동 수집용)
		resolver.setAuthorizationRequestCustomizer(c -> c.additionalParameters(p -> {
			p.put("access_type", "offline");
			p.put("prompt", "consent");
		}));

		http.authorizeHttpRequests(a -> a.anyRequest().authenticated())
			.csrf(c -> c.spa()) // XSRF-TOKEN 쿠키 → X-XSRF-TOKEN 헤더 (React 화면용)
			.oauth2Login(o -> o
				.authorizationEndpoint(e -> e.authorizationRequestResolver(resolver))
				.successHandler((req, res, auth) -> {
					var user = (OidcUser) auth.getPrincipal();
					long userId = db.sql("""
							INSERT INTO users (google_sub, email, name) VALUES (?, ?, ?)
							ON CONFLICT (google_sub) DO UPDATE SET email = EXCLUDED.email, name = EXCLUDED.name
							RETURNING id""")
						.params(user.getSubject(), user.getEmail(), user.getFullName())
						.query(Long.class).single();

					var client = authorizedClients.loadAuthorizedClient("google", auth, req);
					if (client != null && client.getRefreshToken() != null) {
						db.sql("""
								INSERT INTO credential (user_id, engine, secret_enc) VALUES (?, 'GOOGLE', ?)
								ON CONFLICT (user_id, engine) DO UPDATE SET secret_enc = EXCLUDED.secret_enc, updated_at = now()""")
							.params(userId, crypto.encrypt(client.getRefreshToken().getTokenValue()))
							.update();
					}
					res.sendRedirect("/api/me");
				}));
		return http.build();
	}
}
