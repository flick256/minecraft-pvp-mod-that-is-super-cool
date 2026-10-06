import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.Random;

/**
 * Renders "Vaelor's Theme": an original heroic battle theme, 150 bpm in D minor, 60 bars (96 s), stereo 44.1 kHz.
 * Taiko and kit drums, a string ostinato, bass, brass fanfare and melody, a choir pad, bells. Pure synthesis.
 */
public class VaelorTheme {
	static final int SR = 44100;
	static final double BPM = 150;
	static final double BEAT = 60.0 / BPM;
	static final double BAR = BEAT * 4;
	static final int BARS = 60;
	static final double LEN = BARS * BAR;
	static final int N = (int) (LEN * SR) + SR / 2;
	static final float[] L = new float[N];
	static final float[] R = new float[N];
	static final float[] RL = new float[N]; // reverb send
	static final float[] RR = new float[N];
	static final Random RNG = new Random(7);

	static double hz(double midi) {
		return 440.0 * Math.pow(2, (midi - 69) / 12.0);
	}

	static void add(int i, double l, double r, double send) {
		if (i < 0 || i >= N) {
			return;
		}
		L[i] += l;
		R[i] += r;
		RL[i] += l * send;
		RR[i] += r * send;
	}

	// --- Oscillators ---
	static double polyBlep(double t, double dt) {
		if (t < dt) {
			t /= dt;
			return t + t - t * t - 1;
		} else if (t > 1 - dt) {
			t = (t - 1) / dt;
			return t * t + t + t + 1;
		}
		return 0;
	}

	/** A state-variable lowpass/bandpass filter. */
	static final class Svf {
		double low, band;

		double lp(double x, double cutoff, double q) {
			double f = 2 * Math.sin(Math.PI * Math.min(cutoff, SR * 0.45) / SR);
			low += f * band;
			double high = x - low - q * band;
			band += f * high;
			return low;
		}

		double bp(double x, double cutoff, double q) {
			lp(x, cutoff, q);
			return band;
		}
	}

	/** Detuned saws through a lowpass with an envelope: brass, strings, bass. */
	static void saws(double start, double dur, double midi, double amp, double pan, int voices, double detune, double cut0, double cut1, double attack,
		double release, double send, double vibrato) {
		int s0 = (int) (start * SR);
		int len = (int) ((dur + release) * SR);
		double[] ph = new double[voices];
		for (int v = 0; v < voices; v++) {
			ph[v] = RNG.nextDouble();
		}
		Svf f1 = new Svf();
		Svf f2 = new Svf();
		double base = hz(midi);
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double env = t < attack ? t / attack : t < dur ? 1.0 : Math.max(0, 1 - (t - dur) / release);
			env = env * env * (3 - 2 * env);
			double vib = 1 + vibrato * Math.sin(2 * Math.PI * 5.2 * t) * Math.min(1, t / 0.4);
			double sum = 0;
			for (int v = 0; v < voices; v++) {
				double det = voices == 1 ? 0 : (v / (double) (voices - 1) - 0.5) * detune;
				double fr = base * Math.pow(2, det / 1200.0) * vib;
				double dt = fr / SR;
				double saw = 2 * ph[v] - 1 - polyBlep(ph[v], dt);
				sum += saw;
				ph[v] += dt;
				if (ph[v] >= 1) {
					ph[v] -= 1;
				}
			}
			sum /= Math.sqrt(voices);
			double cut = cut0 + (cut1 - cut0) * Math.min(1, t / Math.max(0.01, attack * 1.5));
			double y = f2.lp(f1.lp(sum, cut, 0.7), cut, 0.9);
			y *= env * amp;
			add(s0 + n, y * (1 - pan) , y * (1 + pan), send);
		}
	}

	/** A choir "ah": detuned saws through two formant bandpasses, slow swell, wide. */
	static void choir(double start, double dur, double midi, double amp) {
		int s0 = (int) (start * SR);
		double release = 1.2;
		int len = (int) ((dur + release) * SR);
		int voices = 6;
		double[] ph = new double[voices];
		for (int v = 0; v < voices; v++) {
			ph[v] = RNG.nextDouble();
		}
		Svf a1 = new Svf();
		Svf a2 = new Svf();
		Svf b1 = new Svf();
		Svf b2 = new Svf();
		double base = hz(midi);
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double env = t < 0.6 ? t / 0.6 : t < dur ? 1.0 : Math.max(0, 1 - (t - dur) / release);
			double l = 0;
			double r = 0;
			for (int v = 0; v < voices; v++) {
				double det = (v / (double) (voices - 1) - 0.5) * 22;
				double fr = base * Math.pow(2, det / 1200.0) * (1 + 0.004 * Math.sin(2 * Math.PI * (4.6 + v * 0.3) * t + v));
				double dt = fr / SR;
				double saw = 2 * ph[v] - 1 - polyBlep(ph[v], dt);
				ph[v] += dt;
				if (ph[v] >= 1) {
					ph[v] -= 1;
				}
				if (v % 2 == 0) {
					l += saw;
				} else {
					r += saw;
				}
			}
			double yl = a1.bp(l, 730, 0.5) * 1.0 + a2.bp(l, 1150, 0.6) * 0.6;
			double yr = b1.bp(r, 720, 0.5) * 1.0 + b2.bp(r, 1180, 0.6) * 0.6;
			add(s0 + n, yl * env * amp, yr * env * amp, 0.7);
		}
	}

	/** A bell: inharmonic sine partials with long decays. */
	static void bell(double start, double midi, double amp, double pan) {
		int s0 = (int) (start * SR);
		double[] ratio = {1, 2.0, 2.76, 5.40, 8.93};
		double[] gain = {1, 0.5, 0.45, 0.25, 0.12};
		double[] decay = {2.2, 1.4, 1.1, 0.6, 0.35};
		double base = hz(midi);
		int len = (int) (2.5 * SR);
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double y = 0;
			for (int p = 0; p < ratio.length; p++) {
				y += gain[p] * Math.exp(-t / decay[p]) * Math.sin(2 * Math.PI * base * ratio[p] * t);
			}
			y *= amp * Math.min(1, t / 0.002);
			add(s0 + n, y * (1 - pan), y * (1 + pan), 0.5);
		}
	}

	// --- Drums ---
	static void taiko(double start, double amp) {
		int s0 = (int) (start * SR);
		int len = (int) (0.9 * SR);
		double ph = 0;
		Svf f = new Svf();
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double fr = 52 + 70 * Math.exp(-t / 0.05);
			ph += fr / SR;
			double body = Math.sin(2 * Math.PI * ph) * Math.exp(-t / 0.35);
			double skin = f.lp(RNG.nextDouble() * 2 - 1, 900, 0.8) * Math.exp(-t / 0.04) * 0.8;
			double y = Math.tanh((body + skin) * 1.6) * amp;
			add(s0 + n, y, y, 0.45);
		}
	}

	static void kick(double start, double amp) {
		int s0 = (int) (start * SR);
		int len = (int) (0.35 * SR);
		double ph = 0;
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double fr = 45 + 110 * Math.exp(-t / 0.03);
			ph += fr / SR;
			double y = Math.sin(2 * Math.PI * ph) * Math.exp(-t / 0.16) * amp;
			y += (RNG.nextDouble() * 2 - 1) * Math.exp(-t / 0.004) * 0.3 * amp;
			add(s0 + n, y, y, 0.05);
		}
	}

	static void snare(double start, double amp) {
		int s0 = (int) (start * SR);
		int len = (int) (0.3 * SR);
		Svf hp = new Svf();
		double ph = 0;
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double noise = RNG.nextDouble() * 2 - 1;
			double lowp = hp.lp(noise, 1800, 0.7);
			double bright = noise - lowp;
			ph += 190 / (double) SR;
			double tone = Math.sin(2 * Math.PI * ph) * Math.exp(-t / 0.05);
			double y = (bright * Math.exp(-t / 0.11) * 0.9 + tone * 0.7) * amp;
			add(s0 + n, y * 0.95, y * 1.05, 0.25);
		}
	}

	static void hat(double start, double amp, boolean open) {
		int s0 = (int) (start * SR);
		int len = (int) ((open ? 0.35 : 0.08) * SR);
		Svf lp = new Svf();
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double noise = RNG.nextDouble() * 2 - 1;
			double y = (noise - lp.lp(noise, 7000, 0.7)) * Math.exp(-t / (open ? 0.12 : 0.025)) * amp;
			add(s0 + n, y * 0.7, y * 1.2, 0.1);
		}
	}

	static void crash(double start, double amp) {
		int s0 = (int) (start * SR);
		int len = (int) (2.4 * SR);
		Svf lp = new Svf();
		for (int n = 0; n < len; n++) {
			double t = (double) n / SR;
			double noise = RNG.nextDouble() * 2 - 1;
			double y = (noise - lp.lp(noise, 4500, 0.7)) * Math.exp(-t / 0.7) * amp;
			add(s0 + n, y * 1.1, y * 0.9, 0.35);
		}
	}

	// --- Music ---
	/** Chord roots and qualities (minor/major) as midi triads around D3. */
	static int[] triad(String c) {
		return switch (c) {
			case "Dm" -> new int[] {50, 53, 57};
			case "Bb" -> new int[] {46, 50, 53};
			case "C" -> new int[] {48, 52, 55};
			case "F" -> new int[] {53, 57, 60};
			case "Gm" -> new int[] {55, 58, 62};
			case "A" -> new int[] {45, 49, 52};
			default -> throw new IllegalArgumentException(c);
		};
	}

	static double bar(int b) {
		return b * BAR;
	}

	/** The string ostinato over one bar of chord {@code c}: sixteenths on its tones. */
	static void ostinato(int b, String c, double amp, int octave) {
		int[] t = triad(c);
		int[] pattern = {0, 12, 7, 12, 3, 12, 7, 12, 0, 12, 7, 15, 3, 12, 7, 12};
		for (int i = 0; i < 16; i++) {
			int p = pattern[i];
			int note = p == 7 ? t[2] : p == 3 ? t[1] : p == 15 ? t[1] + 12 : p == 12 ? t[0] + 12 : t[0];
			double accent = i % 4 == 0 ? 1.0 : 0.72;
			saws(bar(b) + i * BEAT / 4, BEAT / 4 * 0.8, note + 12 * octave, amp * accent, i % 2 == 0 ? -0.35 : 0.35, 2, 12, 900, 2600, 0.005, 0.06, 0.18,
				0);
		}
	}

	static void bass(int b, String c, double amp) {
		int root = triad(c)[0] - 12;
		for (int i = 0; i < 8; i++) {
			int note = i == 3 || i == 7 ? root + 12 : root;
			saws(bar(b) + i * BEAT / 2, BEAT / 2 * 0.85, note, amp, 0, 2, 6, 220, 700, 0.004, 0.05, 0.05, 0);
		}
	}

	static void pad(int b, int bars, String c, double amp) {
		int[] t = triad(c);
		for (int n : t) {
			saws(bar(b), bars * BAR - 0.05, n + 12, amp, 0, 3, 18, 500, 1400, 0.5, 0.6, 0.5, 0.003);
		}
	}

	static void brassChord(int b, double beats, String c, double amp, double at) {
		int[] t = triad(c);
		for (int n : t) {
			saws(bar(b) + at * BEAT, beats * BEAT, n + 12, amp, 0, 3, 14, 400, 3200, 0.06, 0.25, 0.35, 0.002);
		}
	}

	/** The main theme: eight bars, (beat, length, midi) per note. */
	static final double[][][] THEME = {
		{{0, 2, 74}, {2, 1, 69}, {3, 1, 74}},
		{{0, 1.5, 77}, {1.5, 0.5, 76}, {2, 1, 74}, {3, 1, 72}},
		{{0, 3, 69}, {3, 1, 72}},
		{{0, 2, 74}, {2, 1, 77}, {3, 1, 81}},
		{{0, 2, 79}, {2, 1, 77}, {3, 1, 76}},
		{{0, 1.5, 77}, {1.5, 0.5, 76}, {2, 1, 74}, {3, 1, 70}},
		{{0, 2, 72}, {2, 1, 69}, {3, 1, 72}},
		{{0, 2, 69}, {2, 2, 73}}};
	static final String[] THEME_CHORDS = {"Dm", "Dm", "F", "Dm", "C", "Bb", "F", "A"};

	static void theme(int b0, double amp, int octave, boolean brass) {
		for (int i = 0; i < 8; i++) {
			for (double[] n : THEME[i]) {
				double start = bar(b0 + i) + n[0] * BEAT;
				double dur = n[1] * BEAT * 0.95;
				if (brass) {
					saws(start, dur, n[2] + 12 * octave, amp, 0, 4, 16, 600, 4200, 0.05, 0.18, 0.4, 0.004);
				} else {
					saws(start, dur, n[2] + 12 * octave, amp * 0.8, -0.2, 3, 10, 1200, 3800, 0.03, 0.2, 0.45, 0.005);
				}
			}
		}
	}

	static void groove(int b, double amp, boolean full) {
		double s = bar(b);
		for (int beat = 0; beat < 4; beat++) {
			double t = s + beat * BEAT;
			if (beat == 0 || beat == 2) {
				kick(t, 0.9 * amp);
			}
			if (full && beat == 2) {
				kick(t + BEAT / 2, 0.6 * amp);
			}
			if (beat == 1 || beat == 3) {
				snare(t, 0.55 * amp);
			}
			for (int e = 0; e < 2; e++) {
				hat(t + e * BEAT / 2, (e == 0 ? 0.22 : 0.14) * amp, full && beat == 3 && e == 1);
			}
		}
	}

	static void taikos(int b, double amp, boolean busy) {
		double s = bar(b);
		taiko(s, amp);
		taiko(s + 2 * BEAT, amp * 0.85);
		if (busy) {
			taiko(s + 1.5 * BEAT, amp * 0.6);
			taiko(s + 3 * BEAT, amp * 0.7);
			taiko(s + 3.5 * BEAT, amp * 0.75);
		}
	}

	static void compose() {
		// 0-3: intro. Taikos, a low drone, the horn call.
		for (int b = 0; b < 4; b++) {
			taikos(b, 0.55 + 0.1 * b, b >= 2);
			saws(bar(b), BAR, 38, 0.10, 0, 2, 8, 200, 420, 0.3, 0.3, 0.3, 0);
		}
		crash(bar(0), 0.25);
		double[][] call = {{0, 1, 62}, {1, 0.5, 69}, {1.5, 2.5, 74}};
		for (int rep = 0; rep < 2; rep++) {
			for (double[] n : call) {
				saws(bar(rep * 2) + n[0] * BEAT, n[1] * BEAT * 0.95, n[2], 0.16, 0, 4, 14, 500, 3800, 0.05, 0.3, 0.5, 0.003);
				saws(bar(rep * 2) + n[0] * BEAT, n[1] * BEAT * 0.95, n[2] - 12, 0.12, 0, 3, 14, 400, 2500, 0.05, 0.3, 0.5, 0.003);
			}
		}
		brassChord(3, 2, "A", 0.07, 2);
		// 4-11: A. Ostinato, bass, groove.
		String[] a = {"Dm", "Dm", "Bb", "Bb", "C", "C", "Dm", "A"};
		for (int i = 0; i < 8; i++) {
			int b = 4 + i;
			ostinato(b, a[i], 0.07, 0);
			bass(b, a[i], 0.13);
			groove(b, 0.8, false);
			if (i % 2 == 0) {
				taiko(bar(b), 0.5);
			}
		}
		crash(bar(4), 0.3);
		// 12-19: B. The theme in the brass.
		for (int i = 0; i < 8; i++) {
			int b = 12 + i;
			ostinato(b, THEME_CHORDS[i], 0.06, 0);
			bass(b, THEME_CHORDS[i], 0.13);
			groove(b, 0.9, i >= 4);
			pad(b, 1, THEME_CHORDS[i], 0.025);
		}
		theme(12, 0.15, 0, true);
		crash(bar(12), 0.35);
		// 20-27: C. Choir and brass chords, bigger drums.
		String[] cc = {"Bb", "C", "Dm", "Dm", "Bb", "C", "A", "A"};
		for (int i = 0; i < 8; i++) {
			int b = 20 + i;
			ostinato(b, cc[i], 0.065, 0);
			bass(b, cc[i], 0.14);
			groove(b, 1.0, true);
			taikos(b, 0.55, i % 2 == 1);
			for (int n : triad(cc[i])) {
				choir(bar(b), BAR - 0.05, n + 12, 0.05);
			}
			brassChord(b, 1, cc[i], 0.06, 0);
			brassChord(b, 1.5, cc[i], 0.07, 2.5);
		}
		crash(bar(20), 0.4);
		// 28-31: the bridge. Taikos and choir, then a snare roll into the return.
		for (int i = 0; i < 4; i++) {
			int b = 28 + i;
			taikos(b, 0.65, true);
			for (int n : triad(i < 2 ? "Bb" : "A")) {
				choir(bar(b), BAR - 0.05, n + 12, 0.055);
			}
			saws(bar(b), BAR, 34 + (i < 2 ? 0 : -1), 0.12, 0, 2, 8, 200, 400, 0.2, 0.2, 0.3, 0);
		}
		for (int i = 0; i < 16; i++) {
			snare(bar(31) + i * BEAT / 4, 0.18 + i * 0.03);
		}
		// 32-39: A'. The theme in the strings, an octave up, over the groove.
		for (int i = 0; i < 8; i++) {
			int b = 32 + i;
			ostinato(b, THEME_CHORDS[i], 0.06, 0);
			bass(b, THEME_CHORDS[i], 0.13);
			groove(b, 0.95, true);
		}
		theme(32, 0.14, 0, false);
		crash(bar(32), 0.45);
		// 40-51: the climax. Theme in brass and choir, bells, everything.
		String[] climax = {"Dm", "Dm", "F", "Dm", "C", "Bb", "F", "A", "Bb", "C", "Dm", "A"};
		for (int i = 0; i < 12; i++) {
			int b = 40 + i;
			ostinato(b, climax[i], 0.065, 0);
			bass(b, climax[i], 0.15);
			groove(b, 1.05, true);
			taikos(b, 0.6, true);
			for (int n : triad(climax[i])) {
				choir(bar(b), BAR - 0.05, n + 12, 0.045);
			}
			if (i >= 8) {
				brassChord(b, 3.5, climax[i], 0.07, 0);
			}
			int[] t = triad(climax[i]);
			bell(bar(b), t[0] + 36, 0.05, 0.4);
			bell(bar(b) + 2 * BEAT, t[2] + 24, 0.04, -0.4);
		}
		theme(40, 0.17, 0, true);
		theme(40, 0.07, 1, false);
		crash(bar(40), 0.5);
		crash(bar(48), 0.4);
		// 52-59: the drive back round: ostinato and taikos, ending on A to start again.
		String[] out = {"Dm", "Bb", "C", "Dm", "Dm", "Bb", "C", "A"};
		for (int i = 0; i < 8; i++) {
			int b = 52 + i;
			ostinato(b, out[i], 0.065, 0);
			bass(b, out[i], 0.14);
			groove(b, 0.9, i % 2 == 1);
			taikos(b, 0.55, i >= 6);
			if (i % 2 == 0) {
				brassChord(b, 2, out[i], 0.06, 0);
			}
		}
		brassChord(59, 4, "A", 0.08, 0);
		crash(bar(52), 0.35);
	}

	// --- Reverb (Freeverb-style) ---
	static void reverb() {
		int[] combs = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
		int[] allp = {556, 441, 341, 225};
		for (int ch = 0; ch < 2; ch++) {
			float[] in = ch == 0 ? RL : RR;
			// No low end into the reverb (it only muddies the drums and bass).
			double hp = 0;
			double prev = 0;
			double k = Math.exp(-2 * Math.PI * 250.0 / SR);
			for (int n = 0; n < N; n++) {
				double x = in[n];
				hp = k * (hp + x - prev);
				prev = x;
				in[n] = (float) hp;
			}
			float[] out = ch == 0 ? L : R;
			int spread = ch == 0 ? 0 : 23;
			float[] wet = new float[N];
			for (int c : combs) {
				float[] buf = new float[c + spread];
				int idx = 0;
				float store = 0;
				for (int n = 0; n < N; n++) {
					float y = buf[idx];
					store = y * 0.8f + store * 0.2f;
					buf[idx] = in[n] * 0.015f + store * 0.84f;
					idx = (idx + 1) % buf.length;
					wet[n] += y;
				}
			}
			for (int a : allp) {
				float[] buf = new float[a + spread];
				int idx = 0;
				for (int n = 0; n < N; n++) {
					float b = buf[idx];
					float y = -wet[n] + b;
					buf[idx] = wet[n] + b * 0.5f;
					idx = (idx + 1) % buf.length;
					wet[n] = y;
				}
			}
			for (int n = 0; n < N; n++) {
				out[n] += wet[n] * 0.9f;
			}
		}
	}

	public static void main(String[] args) throws IOException {
		compose();
		reverb();
		// Master: gentle saturation, then normalise to -1 dB.
		double raw = 0;
		double sum = 0;
		for (int n = 0; n < N; n++) {
			raw = Math.max(raw, Math.max(Math.abs(L[n]), Math.abs(R[n])));
			sum += L[n] * L[n] + R[n] * R[n];
		}
		System.out.println("raw peak " + raw + ", rms " + Math.sqrt(sum / (2.0 * N)));
		double pre = 1.15 / raw;
		double peak = 0;
		for (int n = 0; n < N; n++) {
			L[n] = (float) Math.tanh(L[n] * pre);
			R[n] = (float) Math.tanh(R[n] * pre);
			peak = Math.max(peak, Math.max(Math.abs(L[n]), Math.abs(R[n])));
		}
		double gain = 0.89 / peak;
		int frames = (int) (LEN * SR);
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])))) {
			int bytes = frames * 4;
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
			for (int n = 0; n < frames; n++) {
				// A short fade at the very end so the loop doesn't click.
				double fade = n > frames - SR / 20 ? (frames - n) / (double) (SR / 20) : 1;
				short l = (short) Math.round(Math.max(-1, Math.min(1, L[n] * gain * fade)) * 32767);
				short r = (short) Math.round(Math.max(-1, Math.min(1, R[n] * gain * fade)) * 32767);
				out.writeShort(Short.reverseBytes(l));
				out.writeShort(Short.reverseBytes(r));
			}
		}
		System.out.println("peak " + peak + ", " + LEN + " s");
	}
}
