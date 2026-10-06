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
 * <p><b>The Hollow Crown.</b> A star fell on the plain and did not burn out. The Seven Archons named it Aster
 * and raised the arena round it, and it brightened with every bout. Then came Vaelor of the Ninth Gate, who
 * never lost; in the Year of the Silence the Archons gave him to the star and struck his name from the lists.
 * The arena is no monument but a cage: Vaelor is the lock that holds Aster shut, every bout fought above him
 * is a breath he can take, and the crowd that cheers is its keeper.
 *
 * <p>The trail: the Visitor's Guide in the south gate points to the Founders' Archive; the Archivist's
 * Chronicle points to the Warden's Hall; the Warden's Log points to the Chapel (and the Treasury's ledger);
 * the Chaplain's Litany gives away the lower door in the Cells; below the Cells a stair runs under the field
 * to the Heartwell, where the Seven left their confession.
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
		"Welcome, visitor, to the Celestial Colosseum, greatest of the arenas of the Glass Crown.\n\nThis guide will see you to your seat, and perhaps a little further.",
		"THE BOWL\n\nThree tiers of seats rise above the field, with two concourses between them. From the concourses, aisles climb straight up the stands to every row.",
		"BENEATH THE STANDS\n\nThree rings run all the way round: the Inner Ring behind the podium wall, the Middle Ring under the first concourse, and the Outer Ring under the second.",
		"THE GALLERIES\n\nBetween the rings lie the First, Second and Third Galleries, each of forty-eight sectors, numbered sunwise from the east gate. Every door bears the name of its hall.",
		"THE WAY UP\n\nAt the four corners the Grand Stairs rise in two great flights to the first concourse. Sixteen spiral stairs climb from the Middle and Outer Rings to both concourses.",
		"ABOVE\n\nThe crystal over the field is Aster, the Falling Star. The Seven Archons raised this arena around it in the first year of the Crown, and it has brightened with every bout since.",
		"Those curious about the Founders may consult their Archive, in the Second Gallery, sector 3, east of the field.\n\nThe Archive keeps few visitors now. Do knock."));

	static final Book CHRONICLE = new Book("Chronicle of the Falling Star", "Archivist Tell", List.of(
		"Year 1 of the Crown.\n\nA star fell upon the plain and did not burn out. It hung above its crater, singing, and the Seven Archons came to hear it.",
		"They named it Aster. Where its light fell, wounds closed and blades rang truer. The Archons swore to raise a house worthy of it, and the masons came from every gate.",
		"Year 4.\n\nThe Bowl is finished. The first bout is fought beneath Aster, and the star brightens with every blow. The crowd weeps for joy. So do I.",
		"Year 9.\n\nA champion rises: Vaelor of the Ninth Gate. He does not lose. Not once. Aster burns brightest when he fights, and hums when he leaves the field.",
		"Year 12.\n\nVaelor has fought a thousand bouts and won them all. The Archons grow uneasy. They meet at night, in the halls beneath the north stands.",
		"Year 13. The Year of the Silence.\n\nVaelor's name is struck from every list. The Archons will not speak of it. I am told to stop writing.",
		"I have stopped.\n\nBut Warden Corvin keeps a log, and the Warden sees everything. His hall is in the First Gallery, sector 38, under the north stands."));

	static final Book WARDEN = new Book("The Warden's Log", "Warden Corvin", List.of(
		"Night 1 after the Silence.\n\nThe Archons brought Vaelor to the field at midnight. He came willingly. He thought it was a bout.",
		"They led him to the heart of the compass rose. Aster came down out of the sky, low as the crown wall, and the light took him. He did not cry out.",
		"Night 2.\n\nAster is back in its place above the field, but it is larger now, and there is a shape in it that turns to watch the stands.",
		"Night 9.\n\nThe Archons decree: the bouts must never stop. Every bout pays a tithe of light into the star. The Treasury keeps the count, in the Third Gallery, sector 11.",
		"Night 40.\n\nWhen the bouts paused for the winter storms, the crystal cracked. Something knocked from the inside. We fought on through the snow until it stopped.",
		"Night 41.\n\nI have sealed the lower door and given the words to Sister Imre. If you would know where it leads, ask in the Chapel: Second Gallery, sector 26.",
		"Night 300.\n\nI have watched the stands from this hall for three hundred nights. The crowd still cheers. None of them know what they are cheering for."));

	static final Book LEDGER = new Book("The Tithe Ledger", "The Treasury", List.of(
		"Light owed to the Star, by order of the Seven.\n\nEach bout: one measure.\nEach champion felled: three.\nEach night without a bout: the Star takes its own.",
		"Year 13: 4,112 bouts. Paid.\nYear 14: 4,380 bouts. Paid.\nYear 15: 3,906 bouts. Short by 209. The east stair fell that winter.",
		"Year 40: 9,733 bouts. Paid in full.\n\nThe crystal grows. A second ring of light has been raised to hold it.",
		"(In another hand:)\n\nThe ledger balances. It always balances.\n\nHave you asked yourself what happens on the night it doesn't?"));

	static final Book LITANY = new Book("The Litany of the Lock", "Sister Imre", List.of(
		"We do not pray to Aster.\n\nWe pray for it: that it holds, and that he sleeps.",
		"Blade to shield and blow for blow,\nthe light goes up, the dark below.\nWhile champions fight and crowds still roar,\nthe Unbroken sleeps behind the door.",
		"The Warden asked me to keep the words. I keep them badly, for I have written them here. Forgive me. Someone should know the truth.",
		"The lower door is in the Cells, First Gallery, sector 34, under the north-west stands. The stair goes down beneath the field, to the Heartwell under the rose.",
		"If you go, go quietly.\n\nAnd if you hear knocking, fight. Any bout will do. It only needs a bout."));

	static final Book CONFESSION = new Book("The Confession of the Seven", "The Seven Archons", List.of(
		"To whoever finds this:\n\nWe are the Seven, and we are sorry.",
		"Aster was not a gift. It was a hunger that fell from the sky. It fed on battle, and it made us rich and our champions mighty, and we never asked what it was growing into.",
		"When Vaelor could not be beaten, Aster wanted him. It would have taken him in the end, and grown strong enough to take the rest of us. So we gave him to it first, and sealed it shut with him inside.",
		"Now Vaelor is the lock, and his strength holds the star shut. But even he must be fed. Every bout fought above him is a breath he can take.",
		"The arena is not a monument. It is a cage. The crowd is its keeper, and every champion who steps onto that field turns the key.",
		"So fight, champion. Fight often, and fight well.\n\nAbove you, the star is listening.\nBelow you, he is waiting.",
		"-- the Seven Archons,\nin the last year of the Crown"));

	// --- The rooms' names ---

	static final String[] EPITHETS = {"of Aurel", "of the Ninth Gate", "of Embers", "of the Seven", "of Whispers", "of the Crown", "of Saint Imre",
		"of the Long Night", "of Dawnward", "of the Rose", "of Old Marr", "of the Lantern", "of Stillwater", "of the Glass", "of Hollowmere",
		"of the Victor", "of Asterfall", "of Ironvale"};

	static final String[] ROMAN = {"I", "II", "III"};
}
