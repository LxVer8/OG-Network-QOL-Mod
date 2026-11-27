package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

public class OakHavenFlightTracker implements ClientModInitializer {

    public static final int WIDTH = 120;

    private static long windowEndTime = 0;
    private static long cooldownEndTime = 0;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableOakHavenFlight) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || client.world == null) return;

            String currentDim = client.world.getRegistryKey().getValue().toString();
            if (!currentDim.contains("oakhaven")) return;

            render(context, client, ModConfig.INSTANCE.oakhavenFlightX, ModConfig.INSTANCE.oakhavenFlightY, ModConfig.INSTANCE.oakhavenFlightRightAligned, false);
        });
    }

    private void tick(MinecraftClient client) {
        if (!ModConfig.INSTANCE.enableOakHavenFlight) return;
        if (client.player == null || client.world == null) return;

        String currentDim = client.world.getRegistryKey().getValue().toString();
        if (!currentDim.contains("oakhaven")) return;

        long now = System.currentTimeMillis();
        boolean isFlying = client.player.getAbilities().flying;

        if (cooldownEndTime > 0) {
            if (now >= cooldownEndTime) {
                cooldownEndTime = 0;
                windowEndTime = 0;
            }
            return;
        }

        if (windowEndTime > 0) {
            if (now >= windowEndTime) {
                windowEndTime = 0;
                cooldownEndTime = now + 10000;
            }
        } else if (isFlying) {
            windowEndTime = now + 10000;
        }
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY, boolean rightAligned, boolean preview) {
        if (!preview) {
            if (client.player == null || client.world == null) return;
            String currentDim = client.world.getRegistryKey().getValue().toString();
            if (!currentDim.contains("oakhaven")) return;
        }

        long now = System.currentTimeMillis();
        Text value;
        
        boolean isFlying = client.player != null && client.player.getAbilities().flying;

        if (cooldownEndTime > now) {
            long left = cooldownEndTime - now;
            value = Text.literal(String.format(Locale.ROOT, "%.1fs", left / 1000.0)).formatted(Formatting.RED);
        } else if (windowEndTime > now) {
            long left = windowEndTime - now;
            Formatting color = isFlying ? Formatting.AQUA : Formatting.GOLD;
            value = Text.literal(String.format(Locale.ROOT, "%.1fs", left / 1000.0)).formatted(color);
        } else {
            value = Text.literal("Ready").formatted(Formatting.GREEN);
        }

        Text text = Text.empty()
                .append(Text.literal("OakHaven Flight: ").formatted(Formatting.YELLOW))
                .append(value);

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