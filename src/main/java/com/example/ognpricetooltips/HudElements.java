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
                    v -> ModConfig.INSTANCE.enableSeismicSlamTimer = v),
            new Entry("OakHaven Flight Tracker",
                    "Adds a small text showing the flight time left and cooldown of the OakHaven Armor set. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableOakHavenFlight,
                    v -> ModConfig.INSTANCE.enableOakHavenFlight = v),
            new Entry("OakHaven Boss Slayer Quest",
                    "Shows your current Boss Slayer quest (Jack) in the OakHaven quest tracker. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableQuestBossSlayer,
                    v -> ModConfig.INSTANCE.enableQuestBossSlayer = v),
            new Entry("OakHaven Foraging Quest",
                    "Shows your current Foraging quest (Alice) in the OakHaven quest tracker. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableQuestForaging,
                    v -> ModConfig.INSTANCE.enableQuestForaging = v),
            new Entry("OakHaven Merchant Quest",
                    "Shows your current Merchant quest (Lily) in the OakHaven quest tracker. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableQuestMerchant,
                    v -> ModConfig.INSTANCE.enableQuestMerchant = v),
            new Entry("OakHaven Mining Quest",
                    "Shows your current Mining quest (Trent) in the OakHaven quest tracker. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableQuestMining,
                    v -> ModConfig.INSTANCE.enableQuestMining = v),
            new Entry("OakHaven Mob Slayer Quest",
                    "Shows your current Mob Slayer quest (Cassie) in the OakHaven quest tracker. Only appears when in OakHaven.",
                    () -> ModConfig.INSTANCE.enableQuestMobSlayer,
                    v -> ModConfig.INSTANCE.enableQuestMobSlayer = v),
            new Entry("Daily Quest Tracker",
                    "Shows your pending Daily Quests and their progress. Open /quests once so it can read them. Appears everywhere except OakHaven.",
                    () -> ModConfig.INSTANCE.enableDailyQuestTracker,
                    v -> ModConfig.INSTANCE.enableDailyQuestTracker = v)
    );
}