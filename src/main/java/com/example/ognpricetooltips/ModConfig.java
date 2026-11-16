package com.example.ognpricetooltips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    public enum VoteLocation {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    public boolean enablePriceTooltips = true;
    public boolean enableInventoryValue = true;
    public boolean enableShulkerTooltip = true;
    public boolean enableRuneLevelVisualizer = true;
    public boolean enableRuneTypeVisualizer = true;
    public boolean enableVoteTracker = true;
    
    public String menuKey = "Y+U";

    public boolean hasSeenVoteTrackerMessage = false;
    public long lastVotedMinecraftMP = 0;
    public long lastVotedMBS = 0;
    public long lastVotedBlockAtlas = 0;

    public long customTimerMSL = 0;
    public long customTimerMBS = 0;
    public long customTimerBA = 0;

    public boolean notifiedMSL = true;
    public boolean notifiedMBS = true;
    public boolean notifiedBA = true;

    public VoteLocation voteTrackerLocation = VoteLocation.TOP_RIGHT;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ognpricetooltips.json");

    public static final ModConfig INSTANCE = load();

    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, ModConfig.class);
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