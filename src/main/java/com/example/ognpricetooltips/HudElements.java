package com.example.ognpricetooltips;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class HudElements {

    public record Entry(String name, String tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        public boolean get() { return getter.get(); }
        public void set(boolean value) { setter.accept(value); }
    }

    public static final List<Entry> ALL = List.of(
            new Entry("Vote Tracker",
                    "Adds a list of all voting websites and how long its left until you can vote again.",
                    () -> ModConfig.INSTANCE.enableVoteTracker,
                    v -> ModConfig.INSTANCE.enableVoteTracker = v),
            new Entry("Chat Games Tracker",
                    "Adds a small text with how long its left until the next chat game.",
                    () -> ModConfig.INSTANCE.enableChatGamesTracker,
                    v -> ModConfig.INSTANCE.enableChatGamesTracker = v),
            new Entry("Seismic Slam Tracker",
                    "Adds a small text showing the cooldown of Seismic Slam.",
                    () -> ModConfig.INSTANCE.enableSeismicSlamTimer,
                    v -> ModConfig.INSTANCE.enableSeismicSlamTimer = v)
    );
}