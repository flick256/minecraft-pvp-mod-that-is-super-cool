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

	/** The bot took damage from any source; this breaks its combo. */
	public void recordDamageTaken(double damage) {
		damageTaken += damage;
		currentCombo = 0;
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
		return String.format("swings=%d hits=%d (%.0f%%) crits=%d (%.0f%%) dealt=%.1f taken=%.1f bestCombo=%d K/D=%d/%d",
			swings, hits, hitRate() * 100, crits, critRate() * 100, damageDealt, damageTaken, longestCombo, kills, deaths);
	}
}
