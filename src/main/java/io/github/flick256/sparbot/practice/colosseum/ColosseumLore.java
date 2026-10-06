package io.github.flick256.sparbot.practice.colosseum;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The story told inside the Celestial Colosseum, and the signs and books that tell it.
 *
 * <p><b>The Hollow Crown.</b> A star fell on the plain and did not go out. The Seven built the arena round it,
 * and fighters grew strong in its light. Vaelor the Unbroken won a thousand bouts and lost none. When the star
 * cracked, he went down under the field with its heart and held it shut, swearing to stay until a champion came
 * who could beat him. Two hundred went down after him and none of them won. Beat him, and he can rest: you
 * become the Champion of the Crown, his blade is yours and your name goes on the wall.
 *
 * <p>The trail: the Visitor's Guide (south gate) leads to the Archive; Tell's Chronicle leads to the Warden's
 * Hall; the Warden's Log leads to the Cells (and, for a blessing, the Chapel). From the Cells a stair goes down
 * under the field to the Hall of the Fallen and the Heartwell, where Vaelor waits (see {@link VaelorFight}). Four
 * vaults hidden in the stair halls hold the rest of the story: Vaelor was the seventh of the Seven.
 *
 * <p>Signs and lecterns are blocks of the blueprint; what they say is kept here, keyed by position, as the
 * blueprint is worked out, and written into their block entities when they are placed (so a reset puts a
 * taken book back). Signs are waxed so nobody can change them.
 */
final class ColosseumLore {
	private ColosseumLore() {
	}

	/** A sign's four lines (front, and back for a standing sign), its colour and whether it glows. */
	record SignLore(String[] lines, DyeColor color, boolean glow) {
	}

	/** A book on a lectern. */
	record Book(String title, String author, List<String> pages) {
	}

	private static final Map<Long, Object> LORE = new ConcurrentHashMap<>();

	private static long key(int dx, int y, int dz) {
		return ((long) (dx + 1024) << 42) | ((long) (dz + 1024) << 21) | (y + 1024);
	}

	/** Puts a sign in the blueprint, with what it says. */
	static void sign(BlockState[] c, int dx, int y, int dz, BlockState state, DyeColor color, boolean glow, String... lines) {
		String[] four = new String[4];
		for (int i = 0; i < 4; i++) {
			four[i] = i < lines.length ? lines[i] : "";
		}
		CelestialColosseum.put(c, y, state);
		LORE.put(key(dx, y, dz), new SignLore(four, color, glow));
	}

	/** Puts a lectern holding a book in the blueprint. */
	static void lectern(BlockState[] c, int dx, int y, int dz, BlockState state, Book book) {
		CelestialColosseum.put(c, y, state);
		LORE.put(key(dx, y, dz), book);
	}

	/** Writes the sign's text or puts the book on the lectern just placed at {@code pos} (blueprint coordinates dx, y, dz). */
	static void apply(ServerLevel level, BlockPos pos, int dx, int y, int dz) {
		Object lore = LORE.get(key(dx, y, dz));
		if (lore == null) {
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (lore instanceof SignLore s && be instanceof SignBlockEntity sign) {
			Component[] msg = new Component[4];
			for (int i = 0; i < 4; i++) {
				msg[i] = Component.literal(s.lines()[i]);
			}
			SignText text = new SignText(msg, msg, s.color(), s.glow());
			sign.setText(text, true);
			sign.setText(text, false);
			sign.setWaxed(true);
			sign.setChanged();
		} else if (lore instanceof Book b && be instanceof LecternBlockEntity lectern) {
			ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
			stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(b.title()), b.author(), 0,
				b.pages().stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
			lectern.setBook(stack);
			lectern.setChanged();
		} else {
			return;
		}
		BlockState state = level.getBlockState(pos);
		level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
	}

	// --- The books ---

	static final Book GUIDE = new Book("A Visitor's Guide", "The Stewards", List.of(
		"Welcome to the Celestial Colosseum!\n\nThree tiers of seats, two concourses, and under the stands a whole city of halls. This guide will get you to your seat.",
		"GETTING ABOUT\n\nThree rings run round under the stands: the Inner, Middle and Outer Rings. Between them lie the three Galleries of halls, 48 sectors each, numbered sunwise from the east gate.",
		"GOING UP\n\nThe Grand Stairs climb from each gate to the first concourse. Inside, each Gallery has four stair halls, at the four diagonals: sectors 7, 19, 31 and 43.",
		"ONE NAME MISSING\n\nOne hero of this arena is never named on its walls: Vaelor the Unbroken. If you want to know why, ask at the Archive: Second Gallery, sector 3.",
		"FINDINGS\n\nEvery secret you find in here is remembered (open your advancements). Some walls in the stair halls sound hollow. Kneel by them and listen."));

	static final Book CHRONICLE = new Book("The Chronicle", "Archivist Tell", List.of(
		"Year 1.\n\nA star fell on the plain and did not go out. We named it Aster. Where its light fell, fighters grew stronger, so the Seven built this arena round it.",
		"Year 9.\n\nVaelor of the Ninth Gate fought his first bout. He never lost. Not once, in a thousand bouts.",
		"Year 13.\n\nAster cracked. Its light went wild and the stands shook. If its heart broke open, the whole Crown would fall with it.",
		"Vaelor went down under the field with the star's heart and held it shut with his own strength.\n\nHe swore to stay down there until a champion came who could beat him.",
		"Only then would he know someone was strong enough to keep it after him.\n\nMany went down after him. Ask Warden Corvin how many came back: First Gallery, sector 38."));

	static final Book WARDEN = new Book("The Warden's Log", "Warden Corvin", List.of(
		"I keep the count of those who went down to face him.\n\nAurel the Swift. Brenna Ironhand. Dorn of Embers. Mira the Bright. Old Marr. Ysolde of the Rose. And two hundred more.",
		"Not one of them beat him.\n\nSome came back up, beaten and quiet. Some never came back at all.",
		"The way down is in the Cells: First Gallery, sector 34. The stair in the floor.\n\nSister Imre blesses everyone who goes. Her chapel: Second Gallery, sector 26.",
		"If you are reading this, you are thinking of going.\n\nGo armed. Go ready. He is waiting, and he will not go easy on you. He never has."));

	static final Book LITANY = new Book("The Champion's Blessing", "Sister Imre", List.of(
		"To whoever goes down:\n\nYou will hear his music before you see him. Don't be afraid of it. It is a welcome.",
		"He does not hate you. He has waited a hundred years for someone to beat him.\n\nFight him with everything you have. That is the kindest thing you can do for him.",
		"Take my blessing with you, and come back up.\n\n-- Sister Imre"));

	static final Book LEDGER = new Book("The Champion's Purse", "The Treasury", List.of(
		"Set aside in Year 13, by order of the Seven:\n\nTHE CHAMPION'S PURSE\n\nfor whoever beats Vaelor the Unbroken in fair combat.",
		"Year 14: unclaimed.\nYear 15: unclaimed.\nYear 40: unclaimed.\nYear 112: unclaimed.\n\n(Someone has written underneath: \"Not for long.\")"));

	static final Book FALLEN = new Book("The Roll of the Fallen", "Warden Corvin", List.of(
		"These went down to face Vaelor, and lost.\n\nWe honour them here, at the last door.",
		"Aurel the Swift\nBrenna Ironhand\nDorn of Embers\nIlsa Moonward\nKael Two-Blades\nMira the Bright\nOld Marr\nSable Night\nTobin Farshot\nYsolde of the Rose",
		"...and two hundred more.\n\nThe next name on this roll could be yours. Or your name could go on the other wall: the one for champions."));

	static final Book TELL_PAGE = new Book("The Last Page", "Archivist Tell", List.of(
		"I left this page out of the Chronicle.\n\nVaelor asked me to keep his name off the walls, so that nobody would follow him down and get hurt.",
		"I could not keep that promise. Too many people need to know what he did for us.\n\nI hope you are the one who beats him. He has earned his rest."));

	static final Book MASONS_MARK = new Book("The Masons' Mark", "The Masons", List.of(
		"We built the Bowl in four years.\n\nWe built the stair under the field in four nights, and sealed the Lower Door behind him.",
		"Every stone in this place carries our mark. We hid four vaults in the stair halls, one for each quarter.\n\nFind them all and you will know this place better than we did."));

	static final Book SEVENTH_SEAT = new Book("The Seventh Seat", "The Six", List.of(
		"There were Seven of us who built this arena.\n\nThe seventh was Vaelor.",
		"We kept his seat empty.\n\nWhen someone beats him, he can come back up and sit in it again."));

	static final Book VAELOR_OATH = new Book("Vaelor's Oath", "Vaelor", List.of(
		"I will hold the heart of the star for as long as it takes.\n\nI will not lose on purpose. I will not go easy.",
		"When someone beats me fairly, the heart is theirs to keep, and I can rest.\n\n-- V."));

	static final String[] ROMAN = {"I", "II", "III"};
}
