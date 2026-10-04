package io.github.flick256.sparbot.model;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.ml.Mlp;
import io.github.flick256.sparbot.core.ml.Train;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import org.jspecify.annotations.Nullable;

/**
 * Trains a melee model (sword or UHC) in the background while the server runs: imitation, then self-play in the duel
 * simulator (not the world, so nothing in game is touched). It leaves one core free for the server.
 * Every new best is saved to {@code config/sparbot/models/<name>.json} and can be given to bots at once.
 */
public final class TrainingJob {
	private static @Nullable TrainingJob running;

	private final String name;
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private final java.util.concurrent.CountDownLatch firstModel = new java.util.concurrent.CountDownLatch(1);
	private volatile String status = "starting";

	private TrainingJob(String name) {
		this.name = name;
	}

	public static synchronized @Nullable TrainingJob running() {
		return running;
	}

	/**
	 * Starts a training unless one is running.
	 *
	 * @param from a model to continue from, or null to start from the scripted pro
	 * @param report progress lines (called on the server thread)
	 */
	public static synchronized TrainingJob start(MinecraftServer server, Path modelsDir, Train.Mode mode, String name, int generations, @Nullable Mlp from,
		Consumer<String> report) {
		if (running != null) {
			throw new IllegalStateException("Already training " + running.name + " (" + running.status + ")");
		}
		TrainingJob job = new TrainingJob(name);
		running = job;
		int cores = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
		Thread thread = new Thread(() -> {
			ForkJoinPool pool = new ForkJoinPool(cores);
			try {
				double best = pool.submit(() -> Train.run(mode, from, generations, line -> {
					job.status = line;
					SparBot.LOGGER.info("[train {}] {}", name, line);
					server.execute(() -> report.accept(line));
				}, job.cancelled::get, net -> {
					save(server, modelsDir, name, net);
					job.firstModel.countDown();
				})).get();
				String done = String.format(Locale.ROOT, "Training %s %s: best score against the scripted pro %.2f. Give it to a bot with /sparbot model <bot> %s",
					name, job.cancelled.get() ? "stopped" : "finished", best, name);
				server.execute(() -> report.accept(done));
			} catch (Exception e) {
				SparBot.LOGGER.error("Training {} failed", name, e);
				server.execute(() -> report.accept("Training " + name + " failed: " + e.getMessage()));
			} finally {
				pool.shutdown();
				synchronized (TrainingJob.class) {
					running = null;
				}
			}
		}, "SparBot training " + name);
		thread.setDaemon(true);
		thread.setPriority(Thread.MIN_PRIORITY);
		thread.start();
		return job;
	}

	private static void save(MinecraftServer server, Path modelsDir, String name, Mlp net) {
		try {
			Files.createDirectories(modelsDir);
			Files.writeString(modelsDir.resolve(name + ".json"), net.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not save model {}: {}", name, e.getMessage());
		}
		server.execute(() -> SparBot.models().put(name, net));
	}

	/** Waits until the first model is saved (the imitation step done); false on timeout. */
	public boolean awaitFirstModel(long seconds) throws InterruptedException {
		return firstModel.await(seconds, java.util.concurrent.TimeUnit.SECONDS);
	}

	public void stop() {
		cancelled.set(true);
	}

	public String name() {
		return name;
	}

	public String status() {
		return status;
	}
}
