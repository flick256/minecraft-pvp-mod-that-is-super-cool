import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.util.Random;

/**
 * Renders "The Triumph": what plays when Vaelor falls. His theme's opening turned to D major and slowed to a
 * processional, 96 bpm, 24 bars (60 s), stereo 44.1 kHz: a brass fanfare, the choir, timpani (the taikos), bells
 * and strings, building to the whole band. Pure synthesis, same instruments as VaelorTheme.java.
 */
public class Triumph {
	static final int SR = 44100;
	static final double BPM = 96;
	static final double BEAT = 60.0 / BPM;
	static final double BAR = BEAT * 4;
	static final int BARS = 24;
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
			case "D" -> new int[] {50, 54, 57};
			case "G" -> new int[] {55, 59, 62};
			case "A" -> new int[] {45, 49, 52};
			case "Bm" -> new int[] {47, 50, 54};
			case "Em" -> new int[] {52, 55, 59};
			case "F#m" -> new int[] {54, 57, 61};
			case "Bb" -> new int[] {46, 50, 53};
			case "C" -> new int[] {48, 52, 55};
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

	/** The theme: his opening, in the major and broadened. Eight bars, (beat, length, midi) per note. */
	static final double[][][] THEME = {
		{{0, 2, 74}, {2, 1, 69}, {3, 1, 74}},
		{{0, 1.5, 78}, {1.5, 0.5, 76}, {2, 1, 74}, {3, 1, 73}},
		{{0, 3, 71}, {3, 1, 74}},
		{{0, 2, 76}, {2, 1, 78}, {3, 1, 81}},
		{{0, 2, 83}, {2, 1, 81}, {3, 1, 79}},
		{{0, 1.5, 78}, {1.5, 0.5, 76}, {2, 1, 74}, {3, 1, 71}},
		{{0, 2, 73}, {2, 1, 76}, {3, 1, 79}},
		{{0, 4, 78}}};
	static final String[] THEME_CHORDS = {"D", "D", "G", "Em", "G", "Bm", "A", "D"};

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
		// 0-3: the fanfare. Brass calls over timpani rolls, a held D below.
		for (int b = 0; b < 4; b++) {
			taikos(b, 0.45 + 0.1 * b, b % 2 == 1);
			saws(bar(b), BAR, 38, 0.09, 0, 2, 8, 200, 420, 0.3, 0.3, 0.3, 0);
		}
		crash(bar(0), 0.35);
		double[][] call = {{0, 0.5, 62}, {0.5, 0.5, 62}, {1, 1, 69}, {2, 2, 74}, {4, 0.5, 69}, {4.5, 0.5, 69}, {5, 1, 74}, {6, 2, 78}};
		for (int rep = 0; rep < 2; rep++) {
			for (double[] n : call) {
				double at = bar(rep * 2) + n[0] * BEAT;
				saws(at, n[1] * BEAT * 0.92, n[2], 0.17, -0.1, 4, 14, 500, 4200, 0.03, 0.25, 0.5, 0.003);
				saws(at, n[1] * BEAT * 0.92, n[2] - 12, 0.12, 0.1, 3, 14, 400, 2600, 0.03, 0.25, 0.5, 0.003);
			}
		}
		brassChord(3, 4, "A", 0.07, 0);
		// 4-11: the theme on strings and horns, the choir under it.
		for (int i = 0; i < 8; i++) {
			int b = 4 + i;
			String c = THEME_CHORDS[i];
			pad(b, 1, c, 0.05);
			choir(bar(b), BAR, triad(c)[0] + 12, 0.05);
			bass(b, c, 0.10);
			taikos(b, 0.45, false);
			if (i % 2 == 0) {
				bell(bar(b), triad(c)[2] + 24, 0.10, 0.3);
			}
		}
		theme(4, 0.13, 0, false);
		crash(bar(4), 0.25);
		// 12-19: everyone: brass melody an octave up, ostinato, kit, chords on the beat.
		for (int i = 0; i < 8; i++) {
			int b = 12 + i;
			String c = THEME_CHORDS[i];
			ostinato(b, c, 0.06, 0);
			bass(b, c, 0.12);
			groove(b, 0.75, i >= 4);
			taikos(b, 0.6, true);
			pad(b, 1, c, 0.05);
			choir(bar(b), BAR, triad(c)[1] + 12, 0.06);
			brassChord(b, 1, c, 0.05, 0);
			brassChord(b, 1, c, 0.04, 2);
		}
		theme(12, 0.15, 0, true);
		theme(12, 0.07, 1, false);
		crash(bar(12), 0.35);
		crash(bar(16), 0.3);
		// 20-23: the end. A long D major, bells, a last timpani roll.
		crash(bar(20), 0.45);
		brassChord(20, 12, "D", 0.09, 0);
		pad(20, 4, "D", 0.07);
		choir(bar(20), BAR * 3.5, 62, 0.08);
		choir(bar(20), BAR * 3.5, 66, 0.07);
		saws(bar(20), BAR * 3.5, 74, 0.13, 0, 4, 16, 600, 4200, 0.05, 0.4, 1.2, 0.004);
		saws(bar(20), BAR * 3.5, 38, 0.12, 0, 2, 8, 200, 420, 0.1, 0.3, 1.0, 0);
		for (int k = 0; k < 8; k++) {
			bell(bar(20) + k * BEAT * 1.5, new int[] {86, 81, 78, 74, 81, 78, 74, 69}[k], 0.10, k % 2 == 0 ? -0.3 : 0.3);
		}
		for (int k = 0; k < 12; k++) {
			taiko(bar(22) + k * BEAT / 3, 0.25 + k * 0.04);
		}
		taiko(bar(23) + 2 * BEAT, 0.9);
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
