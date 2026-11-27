package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class AnnouncementFilter implements ClientModInitializer {

    public record Announcement(String title, String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        public boolean isHidden() { return getter.getAsBoolean(); }
        public void setHidden(boolean hidden) { setter.accept(hidden); }
    }

    public static final List<Announcement> ANNOUNCEMENTS = List.of(
            new Announcement("black friday sale", "'Black Friday Sale'",
                    () -> ModConfig.INSTANCE.hideAnnouncementBlackFriday, v -> ModConfig.INSTANCE.hideAnnouncementBlackFriday = v),
            new Announcement("proximity chat", "'Proximity Chat'",
                    () -> ModConfig.INSTANCE.hideAnnouncementProximityChat, v -> ModConfig.INSTANCE.hideAnnouncementProximityChat = v),
            new Announcement("join our discord", "'Join Our Discord'",
                    () -> ModConfig.INSTANCE.hideAnnouncementJoinDiscord, v -> ModConfig.INSTANCE.hideAnnouncementJoinDiscord = v),
            new Announcement("report issues", "'Report Issues'",
                    () -> ModConfig.INSTANCE.hideAnnouncementReportIssues, v -> ModConfig.INSTANCE.hideAnnouncementReportIssues = v),
            new Announcement("server rules", "'Server Rules'",
                    () -> ModConfig.INSTANCE.hideAnnouncementServerRules, v -> ModConfig.INSTANCE.hideAnnouncementServerRules = v),
            new Announcement("custom join messages", "'Custom Join Messages'",
                    () -> ModConfig.INSTANCE.hideAnnouncementCustomJoinMessages, v -> ModConfig.INSTANCE.hideAnnouncementCustomJoinMessages = v),
            new Announcement("explore the wild", "'Explore the Wild'",
                    () -> ModConfig.INSTANCE.hideAnnouncementExploreTheWild, v -> ModConfig.INSTANCE.hideAnnouncementExploreTheWild = v),
            new Announcement("afk rewards", "'AFK Rewards'",
                    () -> ModConfig.INSTANCE.hideAnnouncementAfkRewards, v -> ModConfig.INSTANCE.hideAnnouncementAfkRewards = v),
            new Announcement("like us on namemc", "'Like Us on NameMC'",
                    () -> ModConfig.INSTANCE.hideAnnouncementLikeUsOnNameMC, v -> ModConfig.INSTANCE.hideAnnouncementLikeUsOnNameMC = v),
            new Announcement("namemc player likes", "'NameMC Player Likes'",
                    () -> ModConfig.INSTANCE.hideNamemcPlayerLikes, v -> ModConfig.INSTANCE.hideNamemcPlayerLikes = v),
            new Announcement("server shop", "'Server Shop'",
                    () -> ModConfig.INSTANCE.hideAnnouncementServerShop, v -> ModConfig.INSTANCE.hideAnnouncementServerShop = v),
            new Announcement("vote for rewards", "'Vote for Rewards'",
                    () -> ModConfig.INSTANCE.hideAnnouncementVoteForRewards, v -> ModConfig.INSTANCE.hideAnnouncementVoteForRewards = v),
            new Announcement("crate messages", "'Crate Messages'",
                    () -> ModConfig.INSTANCE.hideCrateMessages, v -> ModConfig.INSTANCE.hideCrateMessages = v),
            new Announcement("dungeon messages", "'Dungeon Info'",
                    () -> ModConfig.INSTANCE.hideDungeonMessages, v -> ModConfig.INSTANCE.hideDungeonMessages = v),
            new Announcement("chatgames messages", "'RPG ChatGames'",
                    () -> ModConfig.INSTANCE.hideChatGamesMessages, v -> ModConfig.INSTANCE.hideChatGamesMessages = v)
    );

    private static final Map<String, Announcement> BY_TITLE = new HashMap<>();

    static {
        for (Announcement a : ANNOUNCEMENTS) BY_TITLE.put(a.title(), a);
    }

    private static boolean anyHidden() {
        for (Announcement a : ANNOUNCEMENTS) {
            if (a.isHidden()) return true;
        }
        return false;
    }

    private static final int MIN_DASHES = 20;

    private static final long PENDING_TIMEOUT_MS = 1500;
    private static final long BLOCK_TIMEOUT_MS = 3000;
    private static final int MAX_LINES = 10;

    private enum State { IDLE, PENDING, HIDING }

    private static State state = State.IDLE;
    private static Text heldDivider = null;
    private static long lastLineTime = 0;
    private static int linesInBlock = 0;
    private static boolean isChatGameActive = false;
    private static long chatGameStartTime = 0;
    private static long chatGameClosingAt = 0;
    private static final long CHATGAME_TIMEOUT_MS = 60_000;
    private static final long CHATGAME_CLOSING_MS = 1500;
    private static final int CHATGAME_MAX_ANSWER_WORDS = 3;

    private static final Pattern WIN_SECONDS_LINE =
            Pattern.compile("^\\d+(?:\\.\\d+)?\\s+seconds\\b.*", Pattern.CASE_INSENSITIVE);

    private static Text heldBlank = null;
    private static long heldBlankTime = 0;
    private static final long BLANK_HOLD_MS = 1000;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (checkSpecificMessages(message)) return false;
            if (overlay) return true;
            return shouldShow(message);
        });

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (checkSpecificMessages(message)) return false;
            return sender != null || shouldShow(message);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            long now = System.currentTimeMillis();
            if (state == State.PENDING && now - lastLineTime > PENDING_TIMEOUT_MS) {
                releaseHeldDivider();
                state = State.IDLE;
            }
            if (heldBlank != null && now - heldBlankTime > BLANK_HOLD_MS) {
                releaseHeldBlank();
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            heldDivider = null;
            heldBlank = null;
            state = State.IDLE;
            isChatGameActive = false;
            chatGameClosingAt = 0;
        });
    }

    private static boolean chatGameActive(long now) {
        if (!isChatGameActive) return false;
        if (now - chatGameStartTime > CHATGAME_TIMEOUT_MS
                || (chatGameClosingAt != 0 && now > chatGameClosingAt)) {
            isChatGameActive = false;
            chatGameClosingAt = 0;
            return false;
        }
        return true;
    }

    private static final int MIN_CENTER_SPACES = 10;
    private static final Pattern CENTERED_LEADING =
            Pattern.compile("^[ \\u00A0]{" + MIN_CENTER_SPACES + ",}\\S[\\s\\S]*");
    private static final Pattern CENTERED_INNER =
            Pattern.compile("[\\s\\S]*\\S[ \\u00A0]{" + MIN_CENTER_SPACES + ",}\\S[\\s\\S]*");

    private static boolean isCenteredLine(Text message) {
        String s = message.getString()
                .replaceAll("&#[0-9A-Fa-f]{6}", "")
                .replaceAll("&[a-zA-Z0-9]", "")
                .replaceAll("§[0-9A-Fa-fk-orK-OR]", "")
                .replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}(:\\d{2})?\\] ?", "");
        if (s.trim().isEmpty()) return false;
        if (s.contains("»")) return false;
        return CENTERED_LEADING.matcher(s).matches() || CENTERED_INNER.matcher(s).matches();
    }

    private static String stripTimestamp(String s) {
        return s.replaceFirst("^\\s*\\[\\d{1,2}:\\d{2}(:\\d{2})?\\]\\s*", "");
    }

    private static boolean checkSpecificMessages(Text message) {
        String raw = message.getString().toLowerCase(Locale.ROOT);

        if (ModConfig.INSTANCE.hidePvpDisabledMessage && raw.contains("pvp is disabled")) {
            return true;
        }

        if (ModConfig.INSTANCE.hideRulesMessage && raw.contains("rules") && raw.contains("terms of service")) {
            return true;
        }

        String original = stripTimestamp(message.getString()
                .replaceAll("&#[0-9A-Fa-f]{6}", "")
                .replaceAll("&[a-zA-Z0-9]", "")
                .replaceAll("§[0-9A-Fa-fk-orK-OR]", "")).trim();
        String trimmed = original.toLowerCase(Locale.ROOT);

        if (ModConfig.INSTANCE.hideNamemcPlayerLikes) {
            if (trimmed.contains("has liked us on namemc")) {
                return true;
            }
        }

        if (ModConfig.INSTANCE.hideCrateMessages) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                String playerName = client.player.getName().getString().toLowerCase(Locale.ROOT);
                if (!playerName.isEmpty() && trimmed.contains(playerName)) {
                } else {
                    int arrowIndex = trimmed.indexOf("»");
                    if (arrowIndex != -1) {
                        String prefix = trimmed.substring(0, arrowIndex);
                        if (prefix.contains("crate")) {
                            return true;
                        }
                    }
                }
            }
        }

        if (ModConfig.INSTANCE.hideDungeonMessages) {
            if (trimmed.contains("dungeon info") || trimmed.contains("dungeon will start in")) {
                return true;
            }
        }

        long now = System.currentTimeMillis();
        boolean chatGamesEnabled = ModConfig.INSTANCE.hideChatGamesMessages;

        boolean isStart = trimmed.contains("rpg chatgames") || trimmed.contains("the first to ");
        boolean isStartTail = trimmed.equals("wins!") || trimmed.equals("wins");
        boolean hasSolveVerb = trimmed.contains(" typed ") || trimmed.contains(" solved ")
                || trimmed.contains(" unscrambled ") || trimmed.contains(" answered ")
                || trimmed.contains(" guessed ") || trimmed.contains(" completed ")
                || trimmed.contains(" filled in ");
        boolean isWinPart = !trimmed.contains("»") && hasSolveVerb
                && (trimmed.endsWith(" in") || trimmed.endsWith(" to") || trimmed.contains(" in "));
        boolean isWinEnd = WIN_SECONDS_LINE.matcher(trimmed).matches()
                || trimmed.contains("nobody got it in time");
        boolean isCentered = chatGamesEnabled && isCenteredLine(message);
        if (heldBlank != null) {
            if (isStart || isCentered) {
                heldBlank = null;
            } else {
                releaseHeldBlank();
            }
        }

        if (isStart) {
            isChatGameActive = true;
            chatGameStartTime = now;
            chatGameClosingAt = 0;
        } else if ((isWinPart || isWinEnd) && isChatGameActive) {
            chatGameClosingAt = now + CHATGAME_CLOSING_MS;
        }

        if (chatGamesEnabled) {
            if (isStart || isStartTail || isWinPart || isWinEnd || isCentered) {
                return true;
            }

            if (chatGameActive(now)) {
                if (trimmed.isEmpty()) {
                    return true;
                }
                if (WIN_SECONDS_LINE.matcher(trimmed).matches()) {
                    return true;
                }
                int arrow = original.lastIndexOf("»");
                if (arrow != -1) {
                    String sender = original.substring(0, arrow).trim();
                    String body = original.substring(arrow + 1).trim();
                    boolean systemSender = !sender.isEmpty() && sender.equals(sender.toUpperCase(Locale.ROOT));
                    int words = body.isEmpty() ? 0 : body.split("\\s+").length;
                    if (!systemSender && words > 0 && words <= CHATGAME_MAX_ANSWER_WORDS && body.length() <= 40) {
                        return true;
                    }
                }
            } else if (trimmed.isEmpty()) {
                heldBlank = message;
                heldBlankTime = now;
                return true;
            }
        }

        return false;
    }

    private static boolean shouldShow(Text message) {
        if (!anyHidden()) {
            if (state == State.PENDING) releaseHeldDivider();
            state = State.IDLE;
            return true;
        }

        long now = System.currentTimeMillis();
        if (state == State.HIDING && now - lastLineTime > BLOCK_TIMEOUT_MS) state = State.IDLE;
        if (state == State.PENDING && now - lastLineTime > PENDING_TIMEOUT_MS) {
            releaseHeldDivider();
            state = State.IDLE;
        }

        String line = message.getString().trim();
        boolean divider = line.matches("-{" + MIN_DASHES + ",}");

        if (state == State.IDLE) {
            if (divider) {
                heldDivider = message;
                state = State.PENDING;
                lastLineTime = now;
                return false;
            }
            return true;
        }

        if (state == State.PENDING) {
            if (divider) {
                releaseHeldDivider();
                heldDivider = message;
                lastLineTime = now;
                return false;
            }
            Announcement announcement = BY_TITLE.get(normalize(line));
            if (announcement != null && announcement.isHidden()) {
                heldDivider = null;
                state = State.HIDING;
                linesInBlock = 0;
                lastLineTime = now;
                return false;
            }
            releaseHeldDivider();
            state = State.IDLE;
            return true;
        }

        lastLineTime = now;
        linesInBlock++;
        if (divider) {
            state = State.IDLE;
            return false;
        }
        if (linesInBlock > MAX_LINES) {
            state = State.IDLE;
            return true;
        }
        return false;
    }

    private static String normalize(String line) {
        String s = line.toLowerCase(Locale.ROOT).trim();
        while (s.endsWith("!")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private static void releaseHeldDivider() {
        if (heldDivider != null) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(heldDivider);
            heldDivider = null;
        }
    }

    private static void releaseHeldBlank() {
        if (heldBlank != null) {
            Text t = heldBlank;
            heldBlank = null;
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(t);
        }
    }
}