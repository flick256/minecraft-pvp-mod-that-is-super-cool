package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.brain.DecisionTrace;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.IntStream;

/**
 * Full-kit UHC fights in the simulator, with what happened in them: who won, how long it took, what
 * each side spent its time on, how much the world (lava, fire, falls) hurt, and what was left lying
 * around. For finding what the bots do badly at hundreds of fights a second:
 * {@code java ... UhcLab [fights] [profileA] [profileB] [kit] [terrain]}.
 */
public final class UhcLab {
	private UhcLab() {
	}

	/** Totals over many fights. */
	public static final class Report {
		final AtomicInteger fights = new AtomicInteger();
		final AtomicInteger winsA = new AtomicInteger();
		final AtomicInteger winsB = new AtomicInteger();
		final AtomicInteger timeouts = new AtomicInteger();
		final DoubleAdder ticks = new DoubleAdder();
		final DoubleAdder scoreA = new DoubleAdder();
		final Map<String, DoubleAdder> stats = new ConcurrentHashMap<>();
		final Map<String, DoubleAdder> tactics = new ConcurrentHashMap<>();
		final DoubleAdder leftoverWater = new DoubleAdder();
		final DoubleAdder leftoverLava = new DoubleAdder();
		/** Longest stretch either side spent in one non-melee tactic without its health or the opponent's changing. */
		final AtomicInteger longestStall = new AtomicInteger();
		volatile String stallNote = "";

		void add(String key, double v) {
			stats.computeIfAbsent(key, k -> new DoubleAdder()).add(v);
		}

		public double scoreA() {
			return scoreA.sum() / Math.max(1, fights.get());
		}

		@Override
		public String toString() {
			int n = Math.max(1, fights.get());
			StringBuilder sb = new StringBuilder();
			sb.append(String.format(Locale.ROOT, "%d fights: A won %d, B won %d, timeouts %d, A's score %.3f, mean length %.0f ticks%n", fights.get(), winsA.get(),
				winsB.get(), timeouts.get(), scoreA(), ticks.sum() / n));
			for (Map.Entry<String, DoubleAdder> e : new TreeMap<>(stats).entrySet()) {
				sb.append(String.format(Locale.ROOT, "  %-22s %8.2f per fight%n", e.getKey(), e.getValue().sum() / n));
			}
			double total = tactics.values().stream().mapToDouble(DoubleAdder::sum).sum() / 2;
			sb.append("  time per tactic (A then B):");
			new TreeMap<>(tactics).forEach((k, v) -> sb.append(String.format(Locale.ROOT, " %s %.1f%%", k, 100 * v.sum() / Math.max(1, total))));
			sb.append(String.format(Locale.ROOT, "%n  left lying around at the end: water %.2f, lava %.2f blocks%n", leftoverWater.sum() / n, leftoverLava.sum() / n));
			sb.append(String.format(Locale.ROOT, "  longest stall: %d ticks %s%n", longestStall.get(), stallNote));
			return sb.toString();
		}
	}

	public static Report run(Tournament.Entrant a, Tournament.Entrant b, Loadout loadout, SimArena arena, int fights, long seed, int maxTicks) {
		Report report = new Report();
		IntStream.range(0, fights).parallel().forEach(i -> {
			long s = seed * 1_000_003L + i;
			boolean swap = i % 2 == 1;
			Tournament.Entrant first = swap ? b : a;
			Tournament.Entrant second = swap ? a : b;
			int sideA = swap ? 1 : 0;
			int[] stall = new int[2];
			int[] webStart = {-1, -1};
			boolean[] webBucket = new boolean[2];
			String[] stallTactic = new String[2];
			float[][] lastHealth = new float[2][2];
			SimWorld[] worldRef = new SimWorld[1];
			DuelSim.Watcher watcher = (tick, world, fighters, inputs, sides) -> {
				worldRef[0] = world;
				for (int k = 0; k < 2; k++) {
					if (fighters[k].inWeb && webStart[k] < 0) {
						webStart[k] = tick;
						boolean hotbar = false;
						for (int slot = 0; slot < 9; slot++) {
							hotbar |= fighters[k].slots[slot] != null && fighters[k].slots[slot].kind() == io.github.flick256.sparbot.core.item.ItemKind.WATER_BUCKET;
						}
						webBucket[k] = hotbar;
					} else if (!fighters[k].inWeb && webStart[k] >= 0) {
						int length = tick - webStart[k];
						report.add(webBucket[k] ? "web episodes (water in hotbar)" : "web episodes (no water in hotbar)", 0.5);
						report.add(webBucket[k] ? "web ticks (water in hotbar)" : "web ticks (no water in hotbar)", length / 2.0);
						webStart[k] = -1;
					}
					DecisionTrace trace = sides[k].policy().lastTrace();
					String tactic = trace == null || trace.tactic() == null ? "?" : trace.tactic();
					report.tactics.computeIfAbsent((k == sideA ? "A:" : "B:") + tactic, t -> new DoubleAdder()).add(1);
					boolean changed = fighters[k].health != lastHealth[k][0] || fighters[1 - k].health != lastHealth[k][1];
					lastHealth[k][0] = fighters[k].health;
					lastHealth[k][1] = fighters[1 - k].health;
					if (!tactic.equals(stallTactic[k]) || changed || tactic.equals("engage") || tactic.equals("search")) {
						stall[k] = 0;
						stallTactic[k] = tactic;
					} else if (++stall[k] > report.longestStall.get()) {
						report.longestStall.set(stall[k]);
						report.stallNote = "(" + tactic + " " + trace.note() + ", seed " + s + ", tick " + tick + ")";
					}
				}
			};
			DuelSim.Result r = DuelSim.fight(first.side(s), second.side(s ^ 0x9E3779B9L), loadout, arena, s, maxTicks, watcher);
			report.fights.incrementAndGet();
			report.ticks.add(r.ticks());
			report.scoreA.add(r.score(sideA));
			if (r.winner() < 0) {
				report.timeouts.incrementAndGet();
			} else if (r.winner() == sideA) {
				report.winsA.incrementAndGet();
			} else {
				report.winsB.incrementAndGet();
			}
			for (int k = 0; k < 2; k++) {
				DuelSim.SideStats st = r.sides()[k];
				String side = k == sideA ? "A " : "B ";
				report.add(side + "hits", r.hits()[k]);
				report.add(side + "swings", r.swings()[k]);
				report.add(side + "damage dealt", r.damage()[k]);
				report.add(side + "crits", r.crits()[k]);
				report.add(side + "blocked hits", r.blockedHits()[k]);
				report.add(side + "gapples eaten", r.gapplesEaten()[k]);
				report.add(side + "lava pours", st.lavaPours());
				report.add(side + "water pours", st.waterPours());
				report.add(side + "scoops", st.scoops());
				report.add(side + "blocks placed", st.blocksPlaced());
				report.add(side + "webs placed", st.websPlaced());
				report.add(side + "arrows shot", st.arrowsShot());
				report.add(side + "arrow hits", st.arrowHits());
				report.add(side + "lava damage taken", st.lavaDamage());
				report.add(side + "fire damage taken", st.fireDamage());
				report.add(side + "fall damage taken", st.fallDamage());
				report.add(side + "ticks in web", st.ticksInWeb());
				report.add(side + "ticks in water", st.ticksInWater());
				report.add(side + "ticks burning", st.ticksBurning());
			}
			if (worldRef[0] != null) {
				report.leftoverWater.add(worldRef[0].count(SimWorld.WATER));
				report.leftoverLava.add(worldRef[0].count(SimWorld.LAVA));
			}
		});
		return report;
	}

	/** Prints one fight tick by tick between two ticks: {@code UhcLab trace seed from to [a] [b]}. */
	static void trace(long seed, int from, int to, String pa, String pb, String kit, boolean terrain, boolean swap) {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		Tournament.Entrant a = entrant(pa, presets);
		Tournament.Entrant b = entrant(pb, presets);
		Tournament.Entrant first = swap ? b : a;
		Tournament.Entrant second = swap ? a : b;
		DuelSim.Watcher watcher = (tick, world, f, in, sides) -> {
			if (tick < from || tick > to) {
				return;
			}
			StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%5d", tick));
			for (int k = 0; k < 2; k++) {
				DecisionTrace t = sides[k].policy().lastTrace();
				String note = t.note().contains("step=") ? t.note().substring(t.note().indexOf("step=") + 5) : "";
				SimStack held = f[k].held();
				sb.append(String.format(Locale.ROOT, " | %d %-8s %-14s hp%5.1f (%6.2f %5.2f %6.2f) yaw%5.0f pit%4.0f %s%s%s%s %-12s k=%s%s%s%s%s",
					k, t.tactic(), note, f[k].health + f[k].absorption, f[k].x, f[k].y, f[k].z, f[k].yaw, f[k].pitch, f[k].inWater ? "W" : "-",
					f[k].inWeb ? "N" : "-", f[k].onFire() ? "F" : "-", f[k].inLava ? "L" : "-", held == null ? "empty" : held.id().replace("minecraft:", ""),
					in[k].forward() + "/" + in[k].strafe(), in[k].jump() ? "J" : "", in[k].attack() ? "A" : "", in[k].use() ? "U" : "",
					in[k].hotbarSlot() >= 0 ? "#" + in[k].hotbarSlot() : ""));
			}
			if (System.getProperty("blocks") != null) {
				for (int k = 0; k < 2; k++) {
					StringBuilder around = new StringBuilder("        " + k + " near:");
					int fx = (int) Math.floor(f[k].x);
					int fy = (int) Math.floor(f[k].y + 1e-7);
					int fz = (int) Math.floor(f[k].z);
					for (int dy = -1; dy <= 2; dy++) {
						for (int dz = -2; dz <= 2; dz++) {
							for (int dx = -2; dx <= 2; dx++) {
								byte t = world.type(fx + dx, fy + dy, fz + dz);
								if (t != SimWorld.AIR && !(t == SimWorld.STONE && fy + dy < 0)) {
									around.append(" ").append(new String[] {"air", "stone", "cobble", "obsidian", "web", "water", "lava"}[t]).append("@")
										.append(fx + dx).append(",").append(fy + dy).append(",").append(fz + dz)
										.append(world.isSource(fx + dx, fy + dy, fz + dz) ? "s" : "");
								}
							}
						}
					}
					System.out.println(around);
				}
			}
			System.out.println(sb);
		};
		DuelSim.Result r = DuelSim.fight(first.side(seed), second.side(seed ^ 0x9E3779B9L), Loadout.ofKit(kit), terrain ? SimArena.UHC_TERRAIN : SimArena.UHC,
			seed, to + 1, watcher);
		System.out.println("winner " + r.winner() + " after " + r.ticks());
	}

	/**
	 * A contestant by name: a profile id (the scripted brain), {@code melee:<model>} (the pro with a bundled
	 * melee model) or {@code melee:<path.json>}.
	 */
	static Tournament.Entrant entrant(String name, Map<String, SkillProfile> presets) {
		if (name.startsWith("melee:")) {
			// melee:<model>[@<profile>]: the model at that tier's limits (pro by default).
			String model = name.substring("melee:".length());
			String tier = "pro";
			if (model.contains("@")) {
				tier = model.substring(model.indexOf('@') + 1);
				model = model.substring(0, model.indexOf('@'));
			}
			io.github.flick256.sparbot.core.ml.Mlp net;
			if (model.endsWith(".json")) {
				try {
					net = io.github.flick256.sparbot.core.ml.Mlp.fromJson(java.nio.file.Files.readString(java.nio.file.Path.of(model)));
				} catch (java.io.IOException e) {
					throw new java.io.UncheckedIOException(e);
				}
			} else {
				net = io.github.flick256.sparbot.core.ml.Models.loadBundled().get(model);
			}
			return io.github.flick256.sparbot.core.ml.SelfPlay.entrant(name, presets.get(tier), net);
		}
		if (name.contains("-no:")) {
			// <profile>-no:<tactic>,<tactic>: the scripted brain never choosing those tactics.
			SkillProfile profile = presets.get(name.substring(0, name.indexOf("-no:")));
			Map<String, Double> weights = new java.util.HashMap<>();
			for (String tactic : name.substring(name.indexOf("-no:") + 4).split(",")) {
				weights.put(tactic, 0.0);
			}
			io.github.flick256.sparbot.core.style.Playstyle style = new io.github.flick256.sparbot.core.style.Playstyle("no-utility", "No utility", "", weights,
				Map.of(), 0);
			return new Tournament.Entrant(name, style.applyTo(profile), seed -> new io.github.flick256.sparbot.core.brain.DuelBrain(profile, style, seed));
		}
		return Tournament.scripted(presets.get(name));
	}

	/** A profile whose item skills are all 1 (the GameTests' TestSupport#certain). */
	static SkillProfile certain(SkillProfile p) {
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(), p.technique(),
			new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), 1, i.gappleHealthFraction(), i.eatHungerBelow(), 1, 1, 1, 1, i.potHealthFraction(),
				1, 1, 1, 1, 1),
			p.mistakeRate(), p.panicHealthFraction());
	}

	/** A pro shooting a standing dummy 15 blocks away (the GameTest arrowParity): shots and hits until it dies. */
	static void arrows(int fights) {
		arrows(fights, false);
	}

	static void arrows(int fights, boolean moving) {
		SkillProfile pro = certain(SkillProfiles.loadPresets().get("pro"));
		Tournament.Entrant shooter = new Tournament.Entrant("archer", pro, seed -> new io.github.flick256.sparbot.core.brain.DuelBrain(pro, seed));
		io.github.flick256.sparbot.core.brain.Policy idle = new io.github.flick256.sparbot.core.brain.Policy() {
			@Override
			public io.github.flick256.sparbot.core.act.Inputs act(io.github.flick256.sparbot.core.sense.Observation observation) {
				return io.github.flick256.sparbot.core.act.Inputs.IDLE;
			}

			@Override
			public DecisionTrace lastTrace() {
				return DecisionTrace.NONE;
			}

			@Override
			public void reset() {
			}
		};
		SimArena arena = new SimArena(16, 6, 15, 15, false, false);
		int shots = 0;
		int hits = 0;
		int ticks = 0;
		SimArena line = new SimArena(16, 6, 15, 15, false, true);
		for (int i = 0; i < fights; i++) {
			double[] home = new double[3];
			int phase = i;
			DuelSim.Watcher mover = !moving ? null : new DuelSim.Watcher() {
				@Override
				public void tick(int tick, SimWorld world, SimFighter[] f, io.github.flick256.sparbot.core.act.Inputs[] in, DuelSim.Side[] sides) {
					if (tick == 0) {
						home[0] = f[1].x;
						home[2] = f[1].z;
					}
					f[1].x = home[0];
					f[1].z = home[2] + 1.5 * Math.sin(2 * Math.PI * (tick + 7 * phase) / 60.0);
					f[1].vx = 0;
					f[1].vz = 0;
					f[1].pendingMotion = null;
				}

				@Override
				public void arrowDone(SimArrow arrow) {
					if (phase < 8 && arrow.closest != null) {
						System.out.printf(Locale.ROOT, "pass: %s d=%.2f dx=%.2f dy=%.2f dz=%.2f%n", arrow.hit ? "HIT" : "MISS", arrow.closest[0], arrow.closest[1], arrow.closest[2], arrow.closest[3]);
					}
				}
			};
			DuelSim.Result r = DuelSim.fight(shooter.side(i), new DuelSim.Side(idle, pro), Loadout.ofKit("sparbot_uhc"), moving ? line : arena, i, 800, mover);
			shots += r.sides()[0].arrowsShot();
			hits += r.sides()[0].arrowHits();
			ticks += r.ticks();
		}
		System.out.printf(Locale.ROOT, "%d fights: %.1f shots, %.1f hits (%.0f%%), %.0f ticks each%n", fights, shots / (double) fights, hits / (double) fights,
			100.0 * hits / Math.max(1, shots), ticks / (double) fights);
	}

	/** The GameTests' webEscape and fireEscape: a pro webbed (four layouts) or set on fire, an idle opponent 8 blocks off. */
	static void escapes(int repeats) {
		SkillProfile pro = SkillProfiles.loadPresets().get("pro");
		Tournament.Entrant bot = Tournament.scripted(pro);
		io.github.flick256.sparbot.core.brain.Policy idle = new io.github.flick256.sparbot.core.brain.Policy() {
			@Override
			public io.github.flick256.sparbot.core.act.Inputs act(io.github.flick256.sparbot.core.sense.Observation observation) {
				return io.github.flick256.sparbot.core.act.Inputs.IDLE;
			}

			@Override
			public DecisionTrace lastTrace() {
				return DecisionTrace.NONE;
			}

			@Override
			public void reset() {
			}
		};
		SimArena arena = new SimArena(16, 6, 8.25, 8.25, false, true);
		for (int layout = 0; layout <= 4; layout++) {
			StringBuilder sb = new StringBuilder(layout < 4 ? "web layout " + layout + ":" : "fire:");
			for (int i = 0; i < repeats; i++) {
				int lay = layout;
				int[] freeAt = {-1};
				DuelSim.Watcher w = (tick, world, f, in, sides) -> {
					if (tick == 0) {
						f[0].x = 0.25;
						f[0].z = 0.25;
						f[1].x = 8.5;
						f[1].z = 0.5;
						switch (lay) {
							case 0 -> world.set(0, 0, 0, SimWorld.WEB);
							case 1 -> world.set(-1, 0, -1, SimWorld.WEB);
							case 2 -> {
								world.set(-1, 0, 0, SimWorld.WEB);
								world.set(-1, 1, 0, SimWorld.WEB);
							}
							case 3 -> world.set(-1, 1, -1, SimWorld.WEB);
							default -> {
							}
						}
					}
					if (lay == 4) {
						if (tick == 10) {
							world.set(0, 0, 0, SimWorld.LAVA);
						} else if (tick == 14) {
							world.set(0, 0, 0, SimWorld.AIR);
						}
						if (tick > 16 && !f[0].onFire() && freeAt[0] < 0) {
							freeAt[0] = tick - 14;
						}
					} else if (tick > 2 && !f[0].inWeb && freeAt[0] < 0) {
						freeAt[0] = tick;
					}
				};
				DuelSim.fight(bot.side(i), new DuelSim.Side(idle, pro), Loadout.ofKit("sparbot_uhc"), arena, 100 + i, 400, w);
				sb.append(' ').append(freeAt[0]);
			}
			System.out.println(sb);
		}
	}

	/**
	 * Where A's melee hits land from: {@code UhcLab reach <a> <b> <kit|sword> [fights]}. Mean distance, the
	 * share from beyond 2.8 and 2.9 blocks, and a histogram from 1.5 to 3.0.
	 */
	static void reach(String pa, String pb, String kit, int fights) {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		Tournament.Entrant a = entrant(pa, presets);
		Tournament.Entrant b = entrant(pb, presets);
		Loadout loadout = kit.equals("sword") ? Loadout.DIAMOND_SWORD : Loadout.ofKit(kit);
		int[] hist = new int[31];
		double[] score = new double[1];
		java.util.stream.IntStream.range(0, fights).parallel().forEach(i -> {
			long seed = 77_000L + i;
			boolean swap = i % 2 == 1;
			DuelSim.Result r = swap ? DuelSim.fight(b.side(seed ^ 0x9E3779B9L), a.side(seed), loadout, seed, loadout.hasKit() ? 3000 : 1200)
				: DuelSim.fight(a.side(seed), b.side(seed ^ 0x9E3779B9L), loadout, seed, loadout.hasKit() ? 3000 : 1200);
			int side = swap ? 1 : 0;
			synchronized (hist) {
				for (int k = 0; k < 31; k++) {
					hist[k] += r.sides()[side].hitDistance()[k];
				}
				score[0] += r.score(side);
			}
		});
		DuelSim.SideStats all = new DuelSim.SideStats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, hist);
		int n = Math.max(1, all.damagingHits());
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%s vs %s (%s): score %.2f, %d hits, mean %.2f blocks, beyond 2.8: %.0f%%, beyond 2.9: %.0f%% |",
			pa, pb, kit, score[0] / fights, all.damagingHits(), all.meanHitDistance(), 100.0 * all.hitsBeyond(2.8) / n, 100.0 * all.hitsBeyond(2.9) / n));
		for (int k = 15; k <= 30; k++) {
			sb.append(String.format(Locale.ROOT, " %.1f:%.0f%%", k / 10.0, 100.0 * hist[k] / n));
		}
		System.out.println(sb);
	}

	/**
	 * What each utility play is worth: for every lava pour, web, block, arrow and water pour of side A, the
	 * damage A dealt and took in the next 60 ticks, against the same for any 60 ticks of the fight:
	 * {@code UhcLab utility <a> <b> [fights]}.
	 */
	static void utility(String pa, String pb, int fights) {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		Tournament.Entrant a = entrant(pa, presets);
		Tournament.Entrant b = entrant(pb, presets);
		Loadout loadout = Loadout.ofKit("sparbot_uhc");
		String[] kinds = {"lava pour", "web", "block", "arrow", "water pour", "any tick"};
		double[][] sums = new double[kinds.length][5];
		java.util.stream.IntStream.range(0, fights).parallel().forEach(i -> {
			long seed = 88_000L + i;
			boolean swap = i % 2 == 1;
			int me = swap ? 1 : 0;
			java.util.List<double[]> health = new java.util.ArrayList<>();
			java.util.List<int[]> events = new java.util.ArrayList<>();
			int[][] last = new int[1][];
			DuelSim.Watcher w = (tick, world, f, in, sides) -> {
				SimFighter s = f[me];
				SimFighter o = f[1 - me];
				health.add(new double[] {s.health + s.absorption, o.health + o.absorption, o.inWeb ? 1 : 0, o.inLava || o.onFire() ? 1 : 0});
				int[] now = {s.lavaPours, s.websPlaced, s.blocksPlaced, s.arrowsShot, s.waterPours};
				if (last[0] != null) {
					for (int k = 0; k < now.length; k++) {
						if (now[k] > last[0][k]) {
							events.add(new int[] {k, tick});
						}
					}
				}
				last[0] = now;
				if (tick % 20 == 0) {
					events.add(new int[] {5, tick});
				}
			};
			if (swap) {
				DuelSim.fight(b.side(seed ^ 0x9E3779B9L), a.side(seed), loadout, SimArena.UHC, seed, 3000, w);
			} else {
				DuelSim.fight(a.side(seed), b.side(seed ^ 0x9E3779B9L), loadout, SimArena.UHC, seed, 3000, w);
			}
			synchronized (sums) {
				for (int[] e : events) {
					int from = e[1];
					int to = Math.min(health.size() - 1, from + 60);
					if (to <= from) {
						continue;
					}
					double dealt = health.get(from)[1] - health.get(to)[1];
					double taken = health.get(from)[0] - health.get(to)[0];
					sums[e[0]][0] += 1;
					sums[e[0]][1] += dealt;
					sums[e[0]][2] += taken;
					// Caught: a web the opponent is in, lava they burn in, within 20 ticks (and not already before).
					int flag = e[0] == 1 ? 2 : e[0] == 0 ? 3 : -1;
					// (Looked at the tick before: a web or lava can catch them the tick it goes down.)
					if (flag > 0 && from > 0 && health.get(from - 1)[flag] == 0) {
						sums[e[0]][4] += 1;
						for (int t = from; t <= Math.min(health.size() - 1, from + 20); t++) {
							if (health.get(t)[flag] > 0) {
								sums[e[0]][3] += 1;
								break;
							}
						}
					}
				}
			}
		});
		System.out.printf(Locale.ROOT, "%s vs %s, %d fights: health change in the 60 ticks after each play (positive dealt = opponent lost health)%n", pa, pb, fights);
		for (int k = 0; k < kinds.length; k++) {
			double n = Math.max(1, sums[k][0]);
			System.out.printf(Locale.ROOT, "  %-11s %6.0f times  dealt %5.2f  taken %5.2f  net %+5.2f%s%n", kinds[k], sums[k][0], sums[k][1] / n, sums[k][2] / n,
				(sums[k][1] - sums[k][2]) / n, k <= 1 ? String.format(Locale.ROOT, "  caught %.0f%% of %.0f (the rest already caught)", 100 * sums[k][3] / Math.max(1, sums[k][4]), sums[k][4]) : "");
		}
	}

	public static void main(String[] args) {
		if (args.length > 0 && args[0].equals("utility")) {
			utility(args[1], args[2], args.length > 3 ? Integer.parseInt(args[3]) : 200);
			return;
		}
		if (args.length > 0 && args[0].equals("reach")) {
			reach(args[1], args[2], args[3], args.length > 4 ? Integer.parseInt(args[4]) : 200);
			return;
		}
		if (args.length > 0 && args[0].equals("escapes")) {
			escapes(args.length > 1 ? Integer.parseInt(args[1]) : 10);
			return;
		}
		if (args.length > 0 && args[0].equals("arrows")) {
			arrows(args.length > 1 ? Integer.parseInt(args[1]) : 20, args.length > 2 && Boolean.parseBoolean(args[2]));
			return;
		}
		if (args.length > 0 && args[0].equals("trace")) {
			long seed = Long.parseLong(args[1]);
			trace(seed, Integer.parseInt(args[2]), Integer.parseInt(args[3]), args.length > 4 ? args[4] : "pro", args.length > 5 ? args[5] : "pro",
				args.length > 6 ? args[6] : "sparbot_uhc", args.length > 7 && Boolean.parseBoolean(args[7]), args.length > 8 && Boolean.parseBoolean(args[8]));
			return;
		}
		int fights = args.length > 0 ? Integer.parseInt(args[0]) : 200;
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		String pa = args.length > 1 ? args[1] : "pro";
		String pb = args.length > 2 ? args[2] : "pro";
		String kit = args.length > 3 ? args[3] : "sparbot_uhc";
		boolean terrain = args.length > 4 && Boolean.parseBoolean(args[4]);
		int maxTicks = args.length > 5 ? Integer.parseInt(args[5]) : 6000;
		Loadout loadout = Loadout.ofKit(kit);
		long start = System.nanoTime();
		Report r = run(entrant(pa, presets), entrant(pb, presets), loadout, terrain ? SimArena.UHC_TERRAIN : SimArena.UHC, fights, 1, maxTicks);
		double seconds = (System.nanoTime() - start) / 1e9;
		System.out.printf(Locale.ROOT, "%s vs %s, kit %s%s%n", pa, pb, kit, terrain ? ", uneven ground" : "");
		System.out.print(r);
		System.out.printf(Locale.ROOT, "%.1f s (%.1f fights/s)%n", seconds, fights / seconds);
	}
}
