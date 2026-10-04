package io.github.flick256.sparbot.core.stats;

/** Per-bot combat statistics for the current session. */
public final class FightStats {
	private int swings;
	private int hits;
	private int crits;
	private double damageDealt;
	private double damageTaken;
	private int currentCombo;
	private int longestCombo;
	private int kills;
	private int deaths;
	private int rangedHits;
	private double rangedDamage;
	private int blastHits;
	private int totemPops;
	private int gapplesEaten;
	private int pearlsThrown;
	private double hitDistanceSum;
	private int hitsWithDistance;
	private int farHits;

	/** A left click was sent, whether or not it connected. */
	public void recordSwing() {
		swings++;
	}

	/** One of the bot's melee attacks damaged its opponent. */
	public void recordHit(double damage, boolean crit) {
		hits++;
		damageDealt += damage;
		if (crit) {
			crits++;
		}
		currentCombo++;
		longestCombo = Math.max(longestCombo, currentCombo);
	}

	/** A melee hit, with how far it landed from (eye to the opponent's hitbox), in blocks. */
	public void recordHit(double damage, boolean crit, double distance) {
		recordHit(damage, crit);
		hitDistanceSum += distance;
		hitsWithDistance++;
		if (distance > 2.9) {
			farHits++;
		}
	}

	/** Mean distance melee hits landed from, 0 if none measured. */
	public double meanHitDistance() {
		return hitsWithDistance == 0 ? 0 : hitDistanceSum / hitsWithDistance;
	}

	/** Share of measured melee hits from beyond 2.9 blocks. */
	public double farHitShare() {
		return hitsWithDistance == 0 ? 0 : farHits / (double) hitsWithDistance;
	}

	/** The bot took damage from any source; this breaks its combo. */
	public void recordDamageTaken(double damage) {
		damageTaken += damage;
		currentCombo = 0;
	}

	/** One of the bot's projectiles damaged someone. */
	public void recordRangedHit(double damage) {
		rangedHits++;
		rangedDamage += damage;
		damageDealt += damage;
	}

	/** An explosion the bot set off (end crystal, TNT minecart) damaged someone else. */
	public void recordBlastHit(double damage) {
		blastHits++;
		damageDealt += damage;
	}

	public int blastHits() {
		return blastHits;
	}

	public void recordTotemPop() {
		totemPops++;
	}

	public void recordGappleEaten() {
		gapplesEaten++;
	}

	public void recordPearlThrown() {
		pearlsThrown++;
	}

	public int rangedHits() {
		return rangedHits;
	}

	public int totemPops() {
		return totemPops;
	}

	public int gapplesEaten() {
		return gapplesEaten;
	}

	public int pearlsThrown() {
		return pearlsThrown;
	}

	public void recordKill() {
		kills++;
	}

	public void recordDeath() {
		deaths++;
		currentCombo = 0;
	}

	public double hitRate() {
		return swings == 0 ? 0 : (double) hits / swings;
	}

	public double critRate() {
		return hits == 0 ? 0 : (double) crits / hits;
	}

	public int swings() {
		return swings;
	}

	public int hits() {
		return hits;
	}

	public int crits() {
		return crits;
	}

	public double damageDealt() {
		return damageDealt;
	}

	public double damageTaken() {
		return damageTaken;
	}

	public int longestCombo() {
		return longestCombo;
	}

	public int kills() {
		return kills;
	}

	public int deaths() {
		return deaths;
	}

	public String summary() {
		return String.format("swings=%d hits=%d (%.0f%%, from %.2f blocks on average, %.0f%% beyond 2.9) crits=%d (%.0f%%) ranged=%d blasts=%d dealt=%.1f taken=%.1f bestCombo=%d pops=%d gapples=%d pearls=%d K/D=%d/%d",
			swings, hits, hitRate() * 100, meanHitDistance(), farHitShare() * 100, crits, critRate() * 100, rangedHits, blastHits, damageDealt, damageTaken, longestCombo, totemPops, gapplesEaten,
			pearlsThrown, kills, deaths);
	}
}
