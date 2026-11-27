package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.client.MinecraftClient;

public class ShulkerBoxValueTooltip implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            boolean showNormal = ModConfig.INSTANCE.enableShulkerTooltip;
            boolean showOakHaven = ModConfig.INSTANCE.enableOakHavenShulkerValue;
            if (!showNormal && !showOakHaven) return;

            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);

                if (container != null) {
                    double totalValue = 0;
                    double starValue = 0;

                    for (ItemStack invStack : container.stream().toList()) {
                        if (invStack.isEmpty()) continue;

                        double starPrice = showOakHaven ? OakHavenPrices.getSellPrice(invStack) : 0;
                        if (starPrice > 0) {
                            starValue += starPrice * invStack.getCount();
                        } else if (showNormal) {
                            totalValue += PriceTooltipsClient.getSellPrice(invStack, MinecraftClient.getInstance()) * invStack.getCount();
                        }
                    }

                    if (totalValue > 0) {
                        lines.add(Text.literal("Total Value: " + PriceTooltipsClient.formatValue(totalValue)).formatted(Formatting.GOLD));
                    }

                    if (starValue > 0) {
                        lines.add(Text.literal("Total Value: " + OakHavenPrices.formatPrice(starValue)).formatted(Formatting.DARK_GREEN));
                    }
                }
            }
        });
    }
}