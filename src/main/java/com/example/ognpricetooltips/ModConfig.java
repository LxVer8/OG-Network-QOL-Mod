package com.example.ognpricetooltips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    public boolean enablePriceTooltips = false;
    public boolean enableInventoryValue = false;
    public boolean enableShulkerTooltip = false;
    public boolean enableItemNameCheck = false;
    public boolean enableOakHavenPrices = false;
    public boolean enableOakHavenInventoryValue = false;
    public boolean enableOakHavenShulkerValue = false;
    public boolean enableTheoreticalSellPrice = true;
    public boolean enableRuneLevelVisualizer = false;
    public boolean enableRuneTypeVisualizer = false;
    public boolean enableVoteTracker = false;
    public boolean enableChatGamesTracker = false;
    public boolean enableEnchantmentColorFix = false;
    public boolean enableOverMaxIndicator = false;
    public boolean enableHighLevelEnchantFix = false;
    public boolean enableRomanToArabicFix = false;
    public boolean voteTrackerRightAligned = false;
    public boolean chatGamesRightAligned = false;
    public boolean chatGamesWaiting = false;
    public long chatGamesNextTime = 0;
    public boolean hideAnnouncementProximityChat = false;
    public boolean hideAnnouncementJoinDiscord = false;
    public boolean hideAnnouncementReportIssues = false;
    public boolean hideAnnouncementServerRules = false;
    public boolean hideAnnouncementCustomJoinMessages = false;
    public boolean hideAnnouncementExploreTheWild = false;
    public boolean hideAnnouncementAfkRewards = false;
    public boolean hideAnnouncementLikeUsOnNameMC = false;
    public boolean hideAnnouncementServerShop = false;
    public boolean hideAnnouncementVoteForRewards = false;
    public boolean enableChatMessageColorRemover = false;
    public boolean enableChatNameColorRemover = false;
    public boolean enableChatRankRemover = false;
    public boolean enableChatTagRemover = false;
    public boolean enableRealNameDisplay = false;
    public boolean enableSeismicSlamTimer = true;
    public int seismicSlamX = 5;
    public int seismicSlamY = 70;
    public boolean seismicSlamRightAligned = false;
    public String menuKey = "Y+U";
    public boolean hasSeenVoteTrackerMessage = false;
    public long lastVotedMinecraftMP = 0;
    public long lastVotedMBS = 0;
    public long lastVotedBlockAtlas = 0;
    public long customTimerMSL = 0;
    public long customTimerMBS = 0;
    public long customTimerBA = 0;
    public boolean notifiedMSL = false;
    public boolean notifiedMBS = false;
    public boolean notifiedBA = false;
    public int voteTrackerX = 5;
    public int voteTrackerY = 10;
    public int chatGamesX = 5;
    public int chatGamesY = 50;
    public boolean chatMessageColorRemoverActive() { return enableChatMessageColorRemover; }
    public boolean chatNameColorRemoverActive()    { return enableChatNameColorRemover; }
    public boolean chatRankRemoverActive()         { return enableChatRankRemover; }
    public boolean chatTagRemoverActive()          { return enableChatTagRemover; }
    public boolean realNameDisplayActive()         { return enableRealNameDisplay; }
    public boolean enableOakHavenFlight = true;
    public int oakhavenFlightX = 5;
    public int oakhavenFlightY = 90;
    public boolean oakhavenFlightRightAligned = false;
    public boolean enableQuestBossSlayer = true;
    public boolean enableQuestForaging = true;
    public boolean enableQuestMerchant = true;
    public boolean enableQuestMining = true;
    public boolean enableQuestMobSlayer = true;
    public boolean questTrackerActive() {
        return enableQuestBossSlayer || enableQuestForaging || enableQuestMerchant || enableQuestMining || enableQuestMobSlayer;
    }
    public boolean enableRuneSuccessVisualizer = true;
    public boolean hidePvpDisabledMessage = false;
    public int questTrackerX = 5;
    public int questTrackerY = 110;
    public boolean enableDailyQuestTracker = true;
    public int dailyQuestX = 5;
    public int dailyQuestY = 180;
    public QuestStyle questStyle = QuestStyle.COMPACT;
    public boolean hideAnnouncementBlackFriday = false;
    public boolean hideRulesMessage = false;
    public boolean hideCrateMessages = false;
    public boolean hideDungeonMessages = false;
    public boolean hideChatGamesMessages = false;
    public boolean hideNamemcPlayerLikes = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ognpricetooltips.json");

    public static final ModConfig INSTANCE = load();

    public enum QuestStyle {
        COMPACT("Compact"),
        BARS("Bars"),
        DETAILED("Detailed"),
        DETAILED_BARS("Detailed + Bars");

        private final String label;

        QuestStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    if (loaded.questStyle == null) loaded.questStyle = QuestStyle.COMPACT;
                    return loaded;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}