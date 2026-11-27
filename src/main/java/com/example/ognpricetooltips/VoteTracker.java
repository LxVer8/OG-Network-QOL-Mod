package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

public class VoteTracker implements ClientModInitializer {

    public static final int WIDTH = 190;

    private static final ZoneId SERVER_TIMEZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    @Override
    public void onInitializeClient() {
        VoteCommand.register();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ModConfig.INSTANCE.hasSeenVoteTrackerMessage) {
                if (client.player != null) {
                    client.player.sendMessage(Text.literal("The timer for the Vote Tracker function in OG-Network QOL Mod will only start if you vote while the game is open and you are on the server.").formatted(Formatting.GOLD), false);
                    client.player.sendMessage(Text.literal("Use /qol vote complete or /qol vote timer to manually set any of the timers as complete or as missing X amount of time.").formatted(Formatting.YELLOW), false);
                }
                ModConfig.INSTANCE.hasSeenVoteTrackerMessage = true;
                ModConfig.INSTANCE.save();
            }
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            handleChatMessage(message);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            handleChatMessage(message);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableVoteTracker) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            render(context, client, ModConfig.INSTANCE.voteTrackerX, ModConfig.INSTANCE.voteTrackerY,
                    ModConfig.INSTANCE.voteTrackerRightAligned);
        });
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY, boolean rightAligned) {
        long nowMillis = System.currentTimeMillis();
        ZonedDateTime now = ZonedDateTime.now(SERVER_TIMEZONE);

        ZonedDateTime mostRecent2AM = now.withHour(2).withMinute(0).withSecond(0).withNano(0);
        if (now.isBefore(mostRecent2AM)) mostRecent2AM = mostRecent2AM.minusDays(1);
        long next2AMMillis = mostRecent2AM.plusDays(1).toInstant().toEpochMilli();
        long lastReset2AM = mostRecent2AM.toInstant().toEpochMilli();

        ZonedDateTime mostRecent9PM = now.withHour(21).withMinute(0).withSecond(0).withNano(0);
        if (now.isBefore(mostRecent9PM)) mostRecent9PM = mostRecent9PM.minusDays(1);
        long next9PMMillis = mostRecent9PM.plusDays(1).toInstant().toEpochMilli();
        long lastReset9PM = mostRecent9PM.toInstant().toEpochMilli();

        boolean mpReady;
        long mpDiff;
        if (ModConfig.INSTANCE.customTimerMSL > 0) {
            mpDiff = Math.max(0, ModConfig.INSTANCE.customTimerMSL - nowMillis);
            mpReady = mpDiff <= 0;
        } else {
            mpReady = ModConfig.INSTANCE.lastVotedMinecraftMP < lastReset2AM;
            mpDiff = mpReady ? 0 : Math.max(0, next2AMMillis - nowMillis);
        }

        if (mpReady && !ModConfig.INSTANCE.notifiedMSL) {
            sendClickableMessage(client, "Minecraft-MP", "https://minecraft-mp.com/server/282687/vote/");
            ModConfig.INSTANCE.notifiedMSL = true;
            ModConfig.INSTANCE.save();
        }

        boolean mbsReady;
        long mbsDiff;
        if (ModConfig.INSTANCE.customTimerMBS > 0) {
            mbsDiff = Math.max(0, ModConfig.INSTANCE.customTimerMBS - nowMillis);
            mbsReady = mbsDiff <= 0;
        } else {
            mbsReady = ModConfig.INSTANCE.lastVotedMBS < lastReset9PM;
            mbsDiff = mbsReady ? 0 : Math.max(0, next9PMMillis - nowMillis);
        }

        if (mbsReady && !ModConfig.INSTANCE.notifiedMBS) {
            sendClickableMessage(client, "MinecraftBestServers", "https://minecraftbestservers.com/server-og-network.313/vote");
            ModConfig.INSTANCE.notifiedMBS = true;
            ModConfig.INSTANCE.save();
        }

        boolean baReady;
        long baDiff;
        if (ModConfig.INSTANCE.customTimerBA > 0) {
            baDiff = Math.max(0, ModConfig.INSTANCE.customTimerBA - nowMillis);
            baReady = baDiff <= 0;
        } else {
            long nextBlockAtlasMillis = ModConfig.INSTANCE.lastVotedBlockAtlas + (24 * 60 * 60 * 1000);
            baReady = ModConfig.INSTANCE.lastVotedBlockAtlas == 0 || nowMillis >= nextBlockAtlasMillis;
            baDiff = baReady ? 0 : Math.max(0, nextBlockAtlasMillis - nowMillis);
        }

        if (baReady && !ModConfig.INSTANCE.notifiedBA) {
            sendClickableMessage(client, "BlockAtlas", "https://blockatlas.net/server-OG-Network.12/vote");
            ModConfig.INSTANCE.notifiedBA = true;
            ModConfig.INSTANCE.save();
        }

        int y = startY;
        drawText(context, client, buildText("Minecraft-MP", mpDiff, mpReady), startX, y, rightAligned);
        y += 12;
        drawText(context, client, buildText("MinecraftBestServers", mbsDiff, mbsReady), startX, y, rightAligned);
        y += 12;
        drawText(context, client, buildText("BlockAtlas", baDiff, baReady), startX, y, rightAligned);
    }

    static void handleChatMessage(Text message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String playerName = client.player.getName().getString();
        String msg = message.getString();

        if (!msg.contains(playerName)) return;

        String lower = msg.toLowerCase(Locale.ROOT);

        if (lower.contains("minecraft-mp.com") || lower.contains("minecraft-mp ")) {
            recordVoteFor("Minecraft-MP");
        } else if (lower.contains("minecraftbestservers") || lower.contains("minecraft best servers")) {
            recordVoteFor("MinecraftBestServers");
        } else if (lower.contains("blockatlas")) {
            recordVoteFor("BlockAtlas");
        }
    }

    private static void recordVoteFor(String site) {
        long now = System.currentTimeMillis();
        switch (site) {
            case "Minecraft-MP" -> {
                ModConfig.INSTANCE.lastVotedMinecraftMP = now;
                ModConfig.INSTANCE.customTimerMSL = 0;
                ModConfig.INSTANCE.notifiedMSL = false;
            }
            case "MinecraftBestServers" -> {
                ModConfig.INSTANCE.lastVotedMBS = now;
                ModConfig.INSTANCE.customTimerMBS = 0;
                ModConfig.INSTANCE.notifiedMBS = false;
            }
            case "BlockAtlas" -> {
                ModConfig.INSTANCE.lastVotedBlockAtlas = now;
                ModConfig.INSTANCE.customTimerBA = 0;
                ModConfig.INSTANCE.notifiedBA = false;
            }
            default -> { return; }
        }
        ModConfig.INSTANCE.save();
    }

    private static void sendClickableMessage(MinecraftClient client, String name, String url) {
        if (client.player == null) return;

        Text prefix = Text.literal("You can now vote in " + name + ": ").formatted(Formatting.GOLD);
        Text link = Text.literal(url)
                .formatted(Formatting.GOLD, Formatting.UNDERLINE)
                .styled(style -> style.withClickEvent(new ClickEvent.OpenUrl(URI.create(url))));

        client.player.sendMessage(Text.empty().append(prefix).append(link), false);
    }

    private static Text buildText(String name, long diff, boolean isReady) {
        Text nameText = Text.literal(name + ": ").formatted(Formatting.YELLOW);
        if (isReady) {
            return Text.empty().append(nameText).append(Text.literal("Ready").formatted(Formatting.GREEN));
        }
        return Text.empty().append(nameText).append(Text.literal(formatDuration(diff)).formatted(Formatting.RED));
    }

    private static String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        return hours + "h " + minutes + "m " + seconds + "s";
    }

    private static void drawText(DrawContext context, MinecraftClient client, Text text, int boxX, int y, boolean rightAligned) {
        int x = rightAligned ? boxX + WIDTH - client.textRenderer.getWidth(text) : boxX;

        Text outlineText = Text.literal(text.getString()).formatted(Formatting.BLACK);

        context.drawText(client.textRenderer, outlineText, x - 1, y - 1, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, outlineText, x, y - 1, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y - 1, 0xFFFFFFFF, false);

        context.drawText(client.textRenderer, outlineText, x - 1, y, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y, 0xFFFFFFFF, false);

        context.drawText(client.textRenderer, outlineText, x - 1, y + 1, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, outlineText, x, y + 1, 0xFFFFFFFF, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y + 1, 0xFFFFFFFF, false);

        context.drawTextWithShadow(client.textRenderer, text, x, y, 0xFFFFFFFF);
    }
}