package io.github.flick256.sparbot.core.record;

/**
 * Who a recorded fighter was.
 *
 * @param profile skill profile id for bots, empty for humans
 * @param style playstyle id for bots, empty for humans
 */
public record FighterInfo(String name, boolean bot, String profile, String style) {
}
