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

public class ShulkerBoxValueTooltip implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (!ModConfig.INSTANCE.enableShulkerTooltip) return;

            if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
                
                if (container != null) {
                    double totalValue = 0;
                    
                    for (ItemStack invStack : container.stream().toList()) {
                        if (invStack.isEmpty()) continue;
                        totalValue += PriceTooltipsClient.getSellPrice(invStack) * invStack.getCount();
                    }

                    if (totalValue > 0) {
                        lines.add(Text.literal("Total Value: " + PriceTooltipsClient.formatValue(totalValue)).formatted(Formatting.GOLD));
                    }
                }
            }
        });
    }
}