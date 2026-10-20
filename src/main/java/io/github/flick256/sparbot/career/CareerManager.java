package io.github.flick256.sparbot.career;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.career.CareerProgress;
import io.github.flick256.sparbot.core.career.CareerStore;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.practice.PracticeLayout;
import io.github.flick256.sparbot.core.rival.Rival;
import io.github.flick256.sparbot.core.rival.Rivals;
import io.github.flick256.sparbot.core.ui.MenuState;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.match.Match;
import io.github.flick256.sparbot.core.match.MatchState;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Server-side named rival challenges, career series and opt-in practice adaptation. */
public final class CareerManager {
	private final CareerStore store;
	private final Map<UUID, Session> sessions = new HashMap<>();
	private final Map<UUID, String> practiceMatches = new HashMap<>();
	private boolean loaded;

	private record Session(UUID player, String mode, String rival, boolean promotion) { }

	public CareerManager(Path file) { store = new CareerStore(file); }

	public void load() throws IOException {
		loaded = false;
		store.load();
		if (store.cancelDanglingSeries()) store.save();
		loaded = true;
	}
	public void save() throws IOException { ensureLoaded(); store.save(); }
	public boolean adaptivePractice(UUID player) { return store.adaptivePractice(player); }
	public void setAdaptivePractice(UUID player, boolean enabled) throws IOException { ensureLoaded(); store.setAdaptivePractice(player, enabled); store.save(); }
	public CareerProgress progress(UUID player, String mode) { return store.progress(player, mode); }

	/** Chooses a bundled profile one step easier/harder after two consecutive losses/wins. */
	public String adaptPracticeProfile(UUID player, String mode, String baseProfile) {
		if (!store.adaptivePractice(player)) return baseProfile;
		int index = SkillProfiles.TIER_IDS.indexOf(baseProfile);
		if (index < 0) return baseProfile;
		int streak = store.adaptiveStreak(player, mode);
		if (streak >= 2) index = Math.min(SkillProfiles.TIER_IDS.size() - 1, index + 1);
		else if (streak <= -2) index = Math.max(0, index - 1);
		return SkillProfiles.TIER_IDS.get(index);
	}

	/** Marks an ordinary practice match for optional adaptation; career rival matches never use this path. */
	public void trackPracticeMatch(UUID player, String mode) { practiceMatches.put(player, mode); }

	/** Starts an authored rival challenge with the player's normal practice terrain and the rival's authored identity. */
	public String challenge(ServerPlayer player, String rivalId) throws IOException {
		Rival rival = Rivals.get(rivalId).orElseThrow(() -> new IllegalArgumentException("Unknown rival " + rivalId));
		if (rival.tier() > store.progress(player.getUUID(), rival.mode()).tier()) throw new IllegalArgumentException("That rival is still locked in your career");
		return start(player, rival, false);
	}

	/** Starts the current mode's next rival in a best-of-five promotion series. */
	public String continueCareer(ServerPlayer player, String modeId) throws IOException {
		ensureLoaded();
		List<Rival> roster = Rivals.inMode(modeId);
		if (roster.isEmpty()) throw new IllegalArgumentException("No career discipline for " + modeId);
		CareerProgress progress = store.progress(player.getUUID(), modeId);
		if (progress.champion()) throw new IllegalArgumentException("You are already champion in " + modeId);
		if (progress.seriesActive()) throw new IllegalArgumentException("Your promotion series is already active");
		Rival rival = roster.stream().filter(r -> r.tier() == progress.tier()).findFirst()
			.orElseThrow(() -> new IllegalStateException("Career rival data is incomplete for " + modeId + " tier " + progress.tier()));
		progress.beginSeries();
		try {
			store.save();
			return start(player, rival, true);
		}
		catch (IOException | RuntimeException e) {
			progress.cancelSeries();
			try { store.save(); }
			catch (IOException recovery) { e.addSuppressed(recovery); }
			throw e;
		}
	}

	private String start(ServerPlayer player, Rival rival, boolean promotion) throws IOException {
		ensureLoaded();
		if (sessions.containsKey(player.getUUID())) throw new IllegalStateException("You already have a rival match in progress");
		if (SparBot.matches().inMatch(player.getUUID())) throw new IllegalStateException("You are already in a match");
		GameMode base = SparBot.matches().modes().get(rival.mode()).orElseThrow(() -> new IllegalArgumentException("Unknown mode " + rival.mode()));
		SkillProfile profile = SparBot.profiles().get(rival.profile()).orElseThrow(() -> new IllegalArgumentException("Unknown rival profile " + rival.profile()));
		// Promotion series are exactly best-of-five, with all ordinary mode rules retained.
		GameMode mode = promotion ? new GameMode(base.id(), base.displayName(), base.description(), base.kit(), CareerProgress.WINS_TO_PROMOTE, base.roundSeconds(),
			base.countdownSeconds(), base.timeoutRule(), base.naturalRegeneration()) : base;
		var style = SparBot.playstyles().resolve(rival.style());
		String message = SparBot.practice().fight(player, mode, profile, SparBot.practice().terrain(player.getUUID()), rival.name(), style);
		String arenaId = PracticeLayout.siteFor(mode.id()).arenaId();
		SparBot.matches().match(arenaId).filter(match -> match.involves(player.getUUID())).ifPresent(Match::enableReviewRecording);
		sessions.put(player.getUUID(), new Session(player.getUUID(), rival.mode(), rival.id(), promotion));
		return message + (promotion ? " — promotion series, best of five" : " — " + rival.description());
	}

	/** Called after match cleanup; only a natural decisive result advances a promotion. */
	public void onMatchFinished(Match match, MatchState.Event event, ServerPlayer pa, ServerPlayer pb) {
		Session involved = sessions.values().stream().filter(s -> match.involves(s.player())).findFirst().orElse(null);
		if (involved != null && (match.forfeit() || event == MatchState.Event.NONE)) { onMatchAborted(involved.player()); return; }
		if (match.forfeit()) {
			Session abandoned = sessions.values().stream().filter(s -> match.involves(s.player())).findFirst().orElse(null);
			if (abandoned != null) onMatchAborted(abandoned.player());
			UUID practicePlayer = practiceMatches.keySet().stream().filter(match::involves).findFirst().orElse(null);
			if (practicePlayer != null) practiceMatches.remove(practicePlayer);
			return;
		}
		if (event != MatchState.Event.MATCH_WON_A && event != MatchState.Event.MATCH_WON_B) return;
		Session session = sessions.values().stream().filter(s -> match.involves(s.player())).findFirst().orElse(null);
		if (session != null && session.promotion()) {
			UUID playerId = session.player();
			sessions.remove(playerId);
			boolean playerWon = match.state().winner() == (match.a().uuid().equals(playerId) ? MatchState.Side.A : MatchState.Side.B);
			CareerProgress progress = store.progress(playerId, session.mode());
			boolean hasNext = Rivals.inMode(session.mode()).stream().anyMatch(r -> r.tier() == progress.tier() + 1);
			progress.completeSeries(playerWon, hasNext);
			store.recordRivalResult(playerId, session.rival(), playerWon);
			try { store.save(); }
			catch (IOException e) { SparBot.LOGGER.error("Could not save career progress for {}", playerId, e); }
			return;
		}
		if (session != null) {
			UUID playerId = session.player();
			sessions.remove(playerId);
			boolean playerWon = match.state().winner() == (match.a().uuid().equals(playerId) ? MatchState.Side.A : MatchState.Side.B);
			store.recordRivalResult(playerId, session.rival(), playerWon);
			try { store.save(); } catch (IOException e) { SparBot.LOGGER.error("Could not save rival record for {}", playerId, e); }
			return;
		}
		UUID adaptivePlayer = practiceMatches.keySet().stream().filter(match::involves).findFirst().orElse(null);
		if (adaptivePlayer == null) return;
		String mode = practiceMatches.remove(adaptivePlayer);
		boolean won = match.state().winner() == (match.a().uuid().equals(adaptivePlayer) ? MatchState.Side.A : MatchState.Side.B);
		if (store.adaptivePractice(adaptivePlayer)) {
			store.recordPracticeResult(adaptivePlayer, mode, won);
			try { store.save(); } catch (IOException e) { SparBot.LOGGER.error("Could not save adaptive practice for {}", adaptivePlayer, e); }
		}
	}

	/** A stopped/aborted match loses its partial score without awarding a result. */
	public void onMatchAborted(UUID player) {
		practiceMatches.remove(player);
		Session session = sessions.remove(player);
		if (session == null || !session.promotion()) return;
		store.progress(player, session.mode()).cancelSeries();
		try { store.save(); } catch (IOException e) { SparBot.LOGGER.error("Could not save cancelled career series for {}", player, e); }
	}

	public List<MenuState.RivalEntry> menuRivals(UUID player) {
		return Rivals.ALL.stream().map(rival -> {
			CareerProgress progress = store.progress(player, rival.mode());
			var record = store.rivalRecord(player, rival.id());
			boolean unlocked = rival.tier() <= progress.tier();
			return new MenuState.RivalEntry(rival.id(), rival.name(), rival.mode(), rival.profile(), rival.style(), rival.description(),
				rival.strength(), rival.weakness(), record.wins(), record.losses(), unlocked);
		}).toList();
	}

	public List<MenuState.CareerEntry> menuCareers(UUID player) {
		return Rivals.ALL.stream().map(Rival::mode).distinct().map(mode -> {
			CareerProgress progress = store.progress(player, mode);
			Rival next = Rivals.inMode(mode).stream().filter(r -> r.tier() == progress.tier()).findFirst().orElse(null);
			return new MenuState.CareerEntry(mode, Integer.toString(progress.tier()), progress.wins(), progress.losses(),
				progress.seriesActive() ? 1 : 0, 1, next == null ? "" : next.name(), progress.champion());
		}).toList();
	}

	/** Stops dangling volatile sessions at shutdown; persisted active series are cancelled too. */
	public void stop() {
		for (Session session : sessions.values()) if (session.promotion()) store.progress(session.player(), session.mode()).cancelSeries();
		sessions.clear();
		practiceMatches.clear();
		if (loaded) try { store.save(); } catch (IOException e) { SparBot.LOGGER.error("Could not save career data at shutdown", e); }
	}

	private void ensureLoaded() {
		if (!loaded) throw new IllegalStateException("Career data is unavailable because its save could not be loaded");
	}
}
