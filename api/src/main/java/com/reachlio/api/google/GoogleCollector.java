package com.reachlio.api.google;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Search Console 실적 → search_metric. 실행마다 collect_run 1행 */
@Component
public class GoogleCollector {

	/** GSC 날짜는 미국 태평양 시간 기준 */
	static final ZoneId GSC_ZONE = ZoneId.of("America/Los_Angeles");

	/** ('','')=일별 합계, (검색어,'')=검색어별, ('',페이지)=페이지별 — V1__init.sql 규칙 */
	static final List<List<String>> DIMENSIONS = List.of(
			List.of("date"), List.of("date", "query"), List.of("date", "page"));

	private static final String UPSERT = """
			INSERT INTO search_metric (connection_id, metric_date, query, page, impressions, clicks, position, source)
			VALUES (?, ?, ?, ?, ?, ?, ?, 'API')
			ON CONFLICT (connection_id, metric_date, query, page) DO UPDATE
			SET impressions = EXCLUDED.impressions, clicks = EXCLUDED.clicks, position = EXCLUDED.position,
			    source = 'API', collected_at = now()""";

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	private static class Target {
		private long userId;
		private String property;
	}

	private final JdbcClient db;
	private final JdbcTemplate jdbc;
	private final GoogleApi google;

	GoogleCollector(JdbcClient db, JdbcTemplate jdbc, GoogleApi google) {
		this.db = db;
		this.jdbc = jdbc;
		this.google = google;
	}

	/** 연결 직후: GSC 보관 기간(16개월) 전체를 한 달씩 */
	@Async
	public void backfill(long connectionId) {
		for (LocalDate[] range : monthlyRanges(LocalDate.now(GSC_ZONE).minusDays(1))) {
			collect(connectionId, range[0], range[1]);
		}
	}

	/** 매일 새벽 4시: 최근 4일 재수집 (GSC 데이터는 2~3일 뒤 확정) */
	@Scheduled(cron = "0 0 4 * * *", zone = "Asia/Seoul")
	public void daily() {
		LocalDate yesterday = LocalDate.now(GSC_ZONE).minusDays(1);
		for (long id : db.sql("SELECT id FROM connection WHERE engine = 'GOOGLE'").query(Long.class).list()) {
			collect(id, yesterday.minusDays(3), yesterday);
		}
	}

	/** 실패해도 예외를 밖으로 던지지 않고 collect_run 에 남긴다 (다른 연결·다른 달은 계속) */
	void collect(long connectionId, LocalDate from, LocalDate to) {
		long runId = db.sql("""
				INSERT INTO collect_run (connection_id, status, date_from, date_to)
				VALUES (?, 'RUNNING', ?, ?) RETURNING id""")
			.params(connectionId, from, to).query(Long.class).single();
		try {
			Target target = db.sql("""
					SELECT s.user_id, c.property FROM connection c JOIN site s ON s.id = c.site_id
					WHERE c.id = ?""")
				.param(connectionId).query(Target.class).single();
			String token = google.accessToken(target.getUserId());
			int upserted = 0;
			for (List<String> dims : DIMENSIONS) {
				List<GoogleApi.Row> rows = google.searchAnalytics(token, target.getProperty(), from, to, dims);
				List<Object[]> params = new ArrayList<Object[]>(rows.size());
				for (GoogleApi.Row row : rows) {
					params.add(toParams(connectionId, dims, row));
				}
				jdbc.batchUpdate(UPSERT, params);
				upserted += params.size();
			}
			db.sql("UPDATE collect_run SET status = 'SUCCESS', finished_at = now(), rows_upserted = ? WHERE id = ?")
				.params(upserted, runId).update();
		} catch (RuntimeException e) {
			db.sql("UPDATE collect_run SET status = 'FAILED', finished_at = now(), error = ? WHERE id = ?")
				.params(String.valueOf(e.getMessage()), runId).update();
		}
	}

	/** API 행 → UPSERT 파라미터 */
	static Object[] toParams(long connectionId, List<String> dims, GoogleApi.Row row) {
		String query = "";
		String page = "";
		if (dims.size() > 1) {
			if ("query".equals(dims.get(1))) {
				query = row.getKeys().get(1);
			} else {
				page = row.getKeys().get(1);
			}
		}
		return new Object[] {connectionId, LocalDate.parse(row.getKeys().get(0)), query, page,
				(int) row.getImpressions(), (int) row.getClicks(),
				BigDecimal.valueOf(row.getPosition()).setScale(2, RoundingMode.HALF_UP)};
	}

	/** 어제 기준 16개월을 한 달 단위 [from, to] 로 (마지막 구간은 어제까지) */
	static List<LocalDate[]> monthlyRanges(LocalDate yesterday) {
		List<LocalDate[]> ranges = new ArrayList<LocalDate[]>();
		for (LocalDate from = yesterday.minusMonths(16).plusDays(1); !from.isAfter(yesterday); from = from.plusMonths(1)) {
			LocalDate to = from.plusMonths(1).minusDays(1);
			ranges.add(new LocalDate[] {from, to.isAfter(yesterday) ? yesterday : to});
		}
		return ranges;
	}
}
