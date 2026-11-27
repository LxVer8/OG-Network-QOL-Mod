package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.regex.Pattern;

public class ChatGamesTracker implements ClientModInitializer {

    public static final int WIDTH = 150;

    private static final long TIMER_MS = 6 * 60 * 1000 + 1000;

    private static final long SPLIT_GAP_MS = 3000;

    private static final Pattern WORD_IN = Pattern.compile("\\bin\\b");
    private static final Pattern WORD_SECONDS = Pattern.compile("\\bseconds?\\b");

    private record LoggedMessage(long time, String lower) {}
    private static final ArrayDeque<LoggedMessage> LOG = new ArrayDeque<>();
    private static final int LOG_SIZE = 1000;

    public enum RefreshStatus { NOT_FOUND, WAITING, TIMER }

    public record RefreshResult(RefreshStatus status, long agoMillis, long leftMillis) {}

    @Override
    public void onInitializeClient() {
        ChatGamesCommand.register();

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            handleMessage(message);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender != null) return;
            handleMessage(message);
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableChatGamesTracker) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            render(context, client, ModConfig.INSTANCE.chatGamesX, ModConfig.INSTANCE.chatGamesY,
                    ModConfig.INSTANCE.chatGamesRightAligned);
        });
    }

    private static void setWaiting() {
        if (!ModConfig.INSTANCE.chatGamesWaiting) {
            ModConfig.INSTANCE.chatGamesWaiting = true;
            ModConfig.INSTANCE.save();
        }
    }

    private static void setNextGame(long time) {
        ModConfig.INSTANCE.chatGamesWaiting = false;
        ModConfig.INSTANCE.chatGamesNextTime = time;
        ModConfig.INSTANCE.save();
    }

    private static boolean isHeader(String lower) {
        return lower.contains("rpg chatgames");
    }

    private static boolean isFinish(LoggedMessage prev, long time, String lower) {
        if (lower.contains("nobody got it in time")) return true;
        if (WORD_IN.matcher(lower).find() && WORD_SECONDS.matcher(lower).find()) return true;

        return prev != null
                && time - prev.time() <= SPLIT_GAP_MS
                && WORD_IN.matcher(prev.lower()).find()
                && WORD_SECONDS.matcher(lower).find();
    }

    private static void handleMessage(Text message) {
        String lower = message.getString().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        LoggedMessage prev;
        synchronized (LOG) {
            prev = LOG.peekLast();
            LOG.addLast(new LoggedMessage(now, lower));
            while (LOG.size() > LOG_SIZE) LOG.removeFirst();
        }

        if (!ModConfig.INSTANCE.enableChatGamesTracker) return;

        if (isHeader(lower)) {
            setWaiting();
        }

        if (ModConfig.INSTANCE.chatGamesWaiting && isFinish(prev, now, lower)) {
            setNextGame(now + TIMER_MS);
        }
    }

    public static RefreshResult refreshFromChat() {
        boolean waiting = false;
        boolean foundHeader = false;
        long finishTime = 0;

        synchronized (LOG) {
            LoggedMessage prev = null;
            for (LoggedMessage m : LOG) {
                if (isHeader(m.lower())) {
                    waiting = true;
                    foundHeader = true;
                }
                if (waiting && isFinish(prev, m.time(), m.lower())) {
                    waiting = false;
                    finishTime = m.time();
                }
                prev = m;
            }
        }

        if (!foundHeader) {
            return new RefreshResult(RefreshStatus.NOT_FOUND, 0, 0);
        }

        long now = System.currentTimeMillis();
        if (waiting) {
            setWaiting();
            return new RefreshResult(RefreshStatus.WAITING, 0, 0);
        }

        setNextGame(finishTime + TIMER_MS);
        return new RefreshResult(RefreshStatus.TIMER, now - finishTime, Math.max(0, finishTime + TIMER_MS - now));
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY, boolean rightAligned) {
        String value;
        Formatting valueColor;

        if (ModConfig.INSTANCE.chatGamesWaiting) {
            value = "Waiting...";
            valueColor = Formatting.GREEN;
        } else {
            long diff = ModConfig.INSTANCE.chatGamesNextTime - System.currentTimeMillis();

            if (diff <= 0) {
                value = "Starting soon...";
            } else {
                Duration duration = Duration.ofMillis(diff);
                value = String.format("%02dm %02ds", duration.toMinutesPart(), duration.toSecondsPart());
            }
            valueColor = Formatting.RED;
        }

        Text text = Text.empty()
                .append(Text.literal("ChatGames: ").formatted(Formatting.YELLOW))
                .append(Text.literal(value).formatted(valueColor));

        drawText(context, client, text, startX, startY, rightAligned);
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