import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Renders "the crowd roars": a stadium's worth of voices rising, cheering and falling away, eight seconds, stereo
 * 44.1 kHz. Filtered noise for the mass of the crowd, a few hundred short shouts (sawtooth voices through vowel
 * formants), claps and whistles, with a little reverb. Pure synthesis.
 */
public class CrowdRoar {
	static final int SR = 44100;
	static final double LEN = 8.0;
	static final int N = (int) (LEN * SR);
	static final float[] L = new float[N];
	static final float[] R = new float[N];
	static final Random RNG = new Random(26);

	/** A state-variable band-pass filter. */
	static final class BandPass {
		final double f;
		final double q;
		double low;
		double band;

		BandPass(double hz, double q) {
			this.f = 2 * Math.sin(Math.PI * Math.min(hz, SR / 6.0) / SR);
			this.q = 1 / q;
		}

		double step(double x) {
			low += f * band;
			double high = x - low - q * band;
			band += f * high;
			return band;
		}
	}

	/** How loud the crowd is at time t: it swells, surges twice, and dies away. */
	static double envelope(double t) {
		double rise = Math.min(1, t / 1.2);
		double fall = t > LEN - 2.8 ? Math.max(0, (LEN - t) / 2.8) : 1;
		double surge = 1 + 0.25 * Math.exp(-Math.pow((t - 2.4) / 0.5, 2)) + 0.2 * Math.exp(-Math.pow((t - 4.3) / 0.6, 2));
		return rise * rise * fall * surge;
	}

	static void mass() {
		BandPass[] l = {new BandPass(450, 1.2), new BandPass(1100, 1.5), new BandPass(2400, 2.0)};
		BandPass[] r = {new BandPass(470, 1.2), new BandPass(1150, 1.5), new BandPass(2300, 2.0)};
		double[] w = {0.9, 0.6, 0.25};
		for (int n = 0; n < N; n++) {
			double e = envelope(n / (double) SR) * 0.32;
			double xl = RNG.nextGaussian();
			double xr = RNG.nextGaussian();
			double sl = 0;
			double sr = 0;
			for (int k = 0; k < 3; k++) {
				sl += l[k].step(xl) * w[k];
				sr += r[k].step(xr) * w[k];
			}
			L[n] += (float) (sl * e);
			R[n] += (float) (sr * e);
		}
	}

	static final double[][] VOWELS = {{730, 1090}, {530, 1840}, {570, 840}, {300, 870}, {660, 1720}};

	static void shouts(int count) {
		for (int i = 0; i < count; i++) {
			double start = 0.2 + RNG.nextDouble() * (LEN - 2.4);
			double dur = 0.35 + RNG.nextDouble() * 0.9;
			double pitch = 110 + RNG.nextDouble() * (RNG.nextBoolean() ? 140 : 320);
			double glide = 1 + RNG.nextDouble() * 0.35;
			double[] v = VOWELS[RNG.nextInt(VOWELS.length)];
			BandPass f1 = new BandPass(v[0], 4);
			BandPass f2 = new BandPass(v[1], 5);
			double pan = RNG.nextDouble() * 2 - 1;
			double amp = 0.05 + RNG.nextDouble() * 0.06;
			double phase = 0;
			int s0 = (int) (start * SR);
			int len = (int) (dur * SR);
			for (int k = 0; k < len && s0 + k < N; k++) {
				double t = k / (double) len;
				double hz = pitch * (1 + (glide - 1) * Math.sin(Math.PI * t * 0.8));
				phase += hz / SR;
				double saw = 2 * (phase - Math.floor(phase)) - 1 + RNG.nextGaussian() * 0.15;
				double y = f1.step(saw) + 0.6 * f2.step(saw);
				double env = Math.sin(Math.PI * Math.min(1, t * 1.2)) * amp * envelope(start + k / (double) SR);
				L[s0 + k] += (float) (y * env * (1 - pan) * 0.5);
				R[s0 + k] += (float) (y * env * (1 + pan) * 0.5);
			}
		}
	}

	static void claps(int count) {
		for (int i = 0; i < count; i++) {
			double start = 0.5 + RNG.nextDouble() * (LEN - 2.0);
			double pan = RNG.nextDouble() * 2 - 1;
			double amp = 0.08 + RNG.nextDouble() * 0.12;
			BandPass bp = new BandPass(1400 + RNG.nextDouble() * 1200, 1.2);
			int s0 = (int) (start * SR);
			for (int k = 0; k < SR / 40 && s0 + k < N; k++) {
				double y = bp.step(RNG.nextGaussian()) * Math.exp(-k / (SR * 0.006)) * amp * envelope(start);
				L[s0 + k] += (float) (y * (1 - pan) * 0.5);
				R[s0 + k] += (float) (y * (1 + pan) * 0.5);
			}
		}
	}

	static void whistles(int count) {
		for (int i = 0; i < count; i++) {
			double start = 0.8 + RNG.nextDouble() * (LEN - 3.5);
			double dur = 0.5 + RNG.nextDouble() * 0.6;
			double hz0 = 1600 + RNG.nextDouble() * 900;
			double pan = RNG.nextDouble() * 2 - 1;
			double phase = 0;
			int s0 = (int) (start * SR);
			int len = (int) (dur * SR);
			for (int k = 0; k < len && s0 + k < N; k++) {
				double t = k / (double) len;
				double hz = hz0 * (1 + 0.35 * t - 0.25 * t * t);
				phase += hz / SR;
				double y = Math.sin(2 * Math.PI * phase) * Math.sin(Math.PI * t) * 0.035;
				L[s0 + k] += (float) (y * (1 - pan) * 0.5);
				R[s0 + k] += (float) (y * (1 + pan) * 0.5);
			}
		}
	}

	/** A short room: a few echoes, low-passed. */
	static void room() {
		int[] taps = {1871, 2953, 4127, 5531, 7243};
		double[] gains = {0.35, 0.28, 0.22, 0.16, 0.11};
		float[] l = L.clone();
		float[] r = R.clone();
		for (int t = 0; t < taps.length; t++) {
			for (int n = taps[t]; n < N; n++) {
				L[n] += (float) (r[n - taps[t]] * gains[t]);
				R[n] += (float) (l[n - taps[t] + (t % 2 == 0 ? 0 : 7)] * gains[t]);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		mass();
		shouts(320);
		claps(900);
		whistles(14);
		room();
		double peak = 0;
		for (int n = 0; n < N; n++) {
			peak = Math.max(peak, Math.max(Math.abs(L[n]), Math.abs(R[n])));
		}
		double gain = 0.85 / peak;
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))) {
			int bytes = N * 4;
			out.writeBytes("RIFF");
			out.writeInt(Integer.reverseBytes(36 + bytes));
			out.writeBytes("WAVEfmt ");
			out.writeInt(Integer.reverseBytes(16));
			out.writeShort(Short.reverseBytes((short) 1));
			out.writeShort(Short.reverseBytes((short) 2));
			out.writeInt(Integer.reverseBytes(SR));
			out.writeInt(Integer.reverseBytes(SR * 4));
			out.writeShort(Short.reverseBytes((short) 4));
			out.writeShort(Short.reverseBytes((short) 16));
			out.writeBytes("data");
			out.writeInt(Integer.reverseBytes(bytes));
			for (int n = 0; n < N; n++) {
				out.writeShort(Short.reverseBytes((short) Math.round(Math.max(-1, Math.min(1, Math.tanh(L[n] * gain))) * 32767)));
				out.writeShort(Short.reverseBytes((short) Math.round(Math.max(-1, Math.min(1, Math.tanh(R[n] * gain))) * 32767)));
			}
		}
		System.out.println("peak " + peak + ", " + LEN + " s");
	}
}
