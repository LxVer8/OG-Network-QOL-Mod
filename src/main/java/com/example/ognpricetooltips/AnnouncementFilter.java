package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Set;
public class AnnouncementFilter implements ClientModInitializer {

    private static final Set<String> TITLES = Set.of(
            "proximity chat",
            "join our discord",
            "report issues",
            "server rules",
            "custom join messages",
            "explore the wild",
            "afk rewards",
            "like us on namemc",
            "server shop",
            "vote for rewards"
    );

    private static final int MIN_DASHES = 20;

    private static final long PENDING_TIMEOUT_MS = 1500;
    private static final long BLOCK_TIMEOUT_MS = 3000;
    private static final int MAX_LINES = 10;

    private enum State { IDLE, PENDING, HIDING }

    private static State state = State.IDLE;
    private static Text heldDivider = null;
    private static long lastLineTime = 0;
    private static int linesInBlock = 0;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || shouldShow(message));

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) ->
                sender != null || shouldShow(message));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (state == State.PENDING && System.currentTimeMillis() - lastLineTime > PENDING_TIMEOUT_MS) {
                releaseHeldDivider();
                state = State.IDLE;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            heldDivider = null;
            state = State.IDLE;
        });
    }

    private static boolean shouldShow(Text message) {
        if (!ModConfig.INSTANCE.enableAnnouncementRemover) {
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
            if (TITLES.contains(normalize(line))) {
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
        String s = line.toLowerCase(java.util.Locale.ROOT).trim();
        while (s.endsWith("!")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private static void releaseHeldDivider() {
        if (heldDivider != null) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(heldDivider);
            heldDivider = null;
        }
    }
}