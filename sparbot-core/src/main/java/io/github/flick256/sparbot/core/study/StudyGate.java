package io.github.flick256.sparbot.core.study;

import java.util.Locale;

/** One personal study credit, scoped to a connection. It never queues more than one game action. */
public final class StudyGate {
	private boolean worldRequired;
	private boolean credit;
	private String pendingCommand;

	public void joined() { worldRequired = true; credit = false; pendingCommand = null; }
	public void disconnected() { worldRequired = false; credit = false; pendingCommand = null; }
	public boolean worldRequired() { return worldRequired; }
	public String pendingCommand() { return pendingCommand; }
	public boolean required() { return worldRequired || pendingCommand != null; }

	/** False holds the command until a question is completed; a second click cannot replace it. */
	public boolean allow(String command) {
		if (!startsActivity(command)) return true;
		if (pendingCommand != null) return false;
		if (!worldRequired && credit) { credit = false; return true; }
		pendingCommand = command;
		return false;
	}

	/** World-entry or optional study pays for the next fight; a queued fight consumes the credit now. */
	public String completed() {
		worldRequired = false;
		String command = pendingCommand;
		pendingCommand = null;
		credit = command == null;
		return command;
	}

	public void cancelFight() { pendingCommand = null; }

	public static boolean startsActivity(String command) {
		if (command == null) return false;
		String normalized = command.strip().replaceFirst("^/", "");
		String[] words = normalized.toLowerCase(Locale.ROOT).split("\\s+");
		if (words.length < 2 || !(words[0].equals("sparbot") || words[0].equals("sparbot:sparbot"))) return false;
		return switch (words[1]) {
			case "spawn", "fight", "rival" -> words.length >= 3;
			case "match" -> words.length >= 3 && words[2].equals("start");
			case "career" -> words.length >= 4 && words[3].equals("continue");
			case "drill" -> words.length >= 3 && !words[2].equals("stop");
			case "practice" -> words.length >= 3 && (words[2].equals("fight") || words[2].equals("rematch")
				|| words.length >= 4 && words[2].equals("colosseum") && (words[3].equals("waves") || words[3].equals("hill")));
			default -> false;
		};
	}
}
