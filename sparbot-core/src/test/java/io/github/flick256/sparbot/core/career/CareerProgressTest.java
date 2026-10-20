package io.github.flick256.sparbot.core.career;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CareerProgressTest {
	@Test void promotionIsAwardedOnlyWhenBestOfFiveMatchFinishes() {
		CareerProgress p = new CareerProgress();
		p.beginSeries();
		p.cancelSeries();
		assertEquals(0, p.wins());
		assertEquals(0, p.losses());
		p.beginSeries();
		assertEquals(CareerProgress.SeriesResult.PROMOTED, p.completeSeries(true, true));
		assertEquals(1, p.tier());
		assertEquals(1, p.wins());
		p.beginSeries();
		assertEquals(CareerProgress.SeriesResult.HELD, p.completeSeries(false, true));
		assertEquals(1, p.losses());
		assertThrows(IllegalStateException.class, () -> p.completeSeries(true, true));
	}

	@Test void championshipLocksTrackAfterPromotion() {
		CareerProgress p = new CareerProgress();
		p.beginSeries();
		p.completeSeries(true, false);
		assertTrue(p.champion());
		assertThrows(IllegalStateException.class, p::beginSeries);
	}
}
