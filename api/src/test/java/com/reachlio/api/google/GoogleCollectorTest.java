package com.reachlio.api.google;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class GoogleCollectorTest {

	@Test
	void monthlyRangesCover16MonthsWithoutGaps() {
		LocalDate yesterday = LocalDate.of(2026, 11, 16);
		List<LocalDate[]> ranges = GoogleCollector.monthlyRanges(yesterday);
		assertEquals(LocalDate.of(2025, 7, 17), ranges.getFirst()[0]);
		assertEquals(yesterday, ranges.getLast()[1]);
		for (int i = 1; i < ranges.size(); i++) {
			assertEquals(ranges.get(i - 1)[1].plusDays(1), ranges.get(i)[0]); // 빈 날짜·겹침 없음
		}
	}

	@Test
	void toParamsFollowsEmptyStringRule() {
		LocalDate day = LocalDate.of(2026, 11, 1);
		GoogleApi.Row total = new GoogleApi.Row(List.of("2026-11-01"), 3, 120, 8.456);
		GoogleApi.Row query = new GoogleApi.Row(List.of("2026-11-01", "tft 시뮬"), 2, 40, 5.0);
		GoogleApi.Row page = new GoogleApi.Row(List.of("2026-11-01", "https://a.com/x"), 1, 30, 9.0);

		assertArrayEquals(new Object[] {7L, day, "", "", 120, 3, new BigDecimal("8.46")},
				GoogleCollector.toParams(7, GoogleCollector.DIMENSIONS.get(0), total));
		assertArrayEquals(new Object[] {7L, day, "tft 시뮬", "", 40, 2, new BigDecimal("5.00")},
				GoogleCollector.toParams(7, GoogleCollector.DIMENSIONS.get(1), query));
		assertArrayEquals(new Object[] {7L, day, "", "https://a.com/x", 30, 1, new BigDecimal("9.00")},
				GoogleCollector.toParams(7, GoogleCollector.DIMENSIONS.get(2), page));
	}
}
