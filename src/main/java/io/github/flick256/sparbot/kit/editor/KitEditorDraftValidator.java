package io.github.flick256.sparbot.kit.editor;

import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.KitValidator;
import java.util.ArrayList;
import java.util.List;

/** Checks editor-specific invariants before a draft reaches registry-aware validation. */
public final class KitEditorDraftValidator {
	private KitEditorDraftValidator() {}

	public static List<String> validate(Kit kit) {
		List<String> errors = new ArrayList<>(KitValidator.validate(kit));
		if (kit.provenance() != null && (!"user-supplied".equals(kit.provenance().source()) || kit.provenance().verified())) {
			errors.add("editor drafts must retain unverified user-supplied provenance");
		}
		return errors;
	}
}
