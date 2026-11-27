package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuneSuccessVisualizer implements ClientModInitializer {

    private static final Pattern SUCCESS_PATTERN = Pattern.compile("(\\d+)%");

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableRuneSuccessVisualizer) return;
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            
            if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;

                int x = context.getScaledWindowWidth() / 2 - 90 + (i * 20) + 2;
                int y = context.getScaledWindowHeight() - 19;

                drawSuccessBar(context, stack, x, y, client);
            }
        });
    }

    public static void renderSuccessIndicators(DrawContext context, HandledScreen<?> screen, MinecraftClient client, int mouseX, int mouseY) {
        if (!ModConfig.INSTANCE.enableRuneSuccessVisualizer) return;
        if (client.player == null) return;

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            drawSuccessBar(context, stack, screen.x + slot.x, screen.y + slot.y, client);
        }

        ItemStack cursorStack = screen.getScreenHandler().getCursorStack();
        if (!cursorStack.isEmpty()) {
            drawSuccessBar(context, cursorStack, mouseX - 8, mouseY - 8, client);
        }
    }

    private static void drawSuccessBar(DrawContext context, ItemStack stack, int x, int y, MinecraftClient client) {
        if (stack.getItem() != Items.PAPER) return;

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
        int successPercent = -1;
        
        for (Text text : tooltip) {
            Matcher matcher = SUCCESS_PATTERN.matcher(text.getString());
            if (matcher.find()) {
                successPercent = Integer.parseInt(matcher.group(1));
                break;
            }
        }
        
        if (successPercent >= 0) {

            int barY = y + 14;
            int barWidth = 14;
            int barX = x + 1;
            int barHeight = 2;

            context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF222222);

            int fillWidth = (int) Math.round((successPercent / 100.0) * barWidth);
            if (fillWidth < 0) fillWidth = 0;
            if (fillWidth > barWidth) fillWidth = barWidth;

            int color;
            if (successPercent < 33) {
                color = 0xFFFF3333;
            } else if (successPercent < 67) {
                color = 0xFFFFAA00;
            } else {
                color = 0xFF55FF55;
            }

            if (fillWidth > 0) {
                context.fill(barX, barY, barX + fillWidth, barY + barHeight, color);
            }
        }
    }
}