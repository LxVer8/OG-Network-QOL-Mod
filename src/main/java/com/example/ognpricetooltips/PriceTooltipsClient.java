package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potion;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PriceTooltipsClient implements ClientModInitializer {

    private static boolean wasMenuKeyPressed = false;

    @Override
    public void onInitializeClient() {
        PriceRegistry.init();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.currentScreen != null) return;

            String combo = ModConfig.INSTANCE.menuKey.toUpperCase();
            if (combo.equals("NONE") || combo.isEmpty()) return;

            String[] keys = combo.split("\\+");
            boolean allPressed = true;

            for (String key : keys) {
                int keyCode = getKeyCode(key.trim());
                if (keyCode == -1 || !InputUtil.isKeyPressed(client.getWindow(), keyCode)) {
                    allPressed = false;
                    break;
                }
            }

            if (allPressed && !wasMenuKeyPressed) {
                client.setScreen(ModMenuIntegration.getConfigScreen(null));
            }
            wasMenuKeyPressed = allPressed;
        });

        ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {
            if (!ModConfig.INSTANCE.enablePriceTooltips) return; 
            
            Item item = stack.getItem();
            
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
                PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
                if (contents != null && contents.potion().isPresent()) {
                    RegistryEntry<Potion> potionType = contents.potion().get();
                    if (item == Items.POTION && PriceRegistry.POTION_PRICES.containsKey(potionType)) {
                        addPriceLore(lines, PriceRegistry.POTION_PRICES.get(potionType), stack.getCount()); return;
                    } else if (item == Items.SPLASH_POTION && PriceRegistry.SPLASH_POTION_PRICES.containsKey(potionType)) {
                        addPriceLore(lines, PriceRegistry.SPLASH_POTION_PRICES.get(potionType), stack.getCount()); return;
                    } else if (item == Items.LINGERING_POTION && PriceRegistry.LINGERING_POTION_PRICES.containsKey(potionType)) {
                        addPriceLore(lines, PriceRegistry.LINGERING_POTION_PRICES.get(potionType), stack.getCount()); return;
                    } else if (item == Items.TIPPED_ARROW && PriceRegistry.TIPPED_ARROW_PRICES.containsKey(potionType)) {
                        addPriceLore(lines, PriceRegistry.TIPPED_ARROW_PRICES.get(potionType), stack.getCount()); return;
                    }
                }
            }

            if (PriceRegistry.ITEM_PRICES.containsKey(item)) {
                PriceRegistry.PriceData prices = PriceRegistry.ITEM_PRICES.get(item);
                addPriceLore(lines, prices, stack.getCount());
            } else {
                lines.add(Text.literal("Cannot be bought or sold").formatted(Formatting.GRAY));
            }
        });
    }

    private static int getKeyCode(String key) {
        if (key.length() == 1) {
            char c = key.charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return GLFW.GLFW_KEY_A + (c - 'A');
            } else if (c >= '0' && c <= '9') {
                return GLFW.GLFW_KEY_0 + (c - '0');
            }
        }
        return -1;
    }

    public static void renderInventoryValues(DrawContext context, HandledScreen<?> screen, MinecraftClient client, int mouseX, int mouseY) {
        if (!ModConfig.INSTANCE.enableInventoryValue) return; 
        if (client.player == null) return;

        double playerValue = 0;
        double containerValue = 0;
        
        List<ItemStack> playerItems = new ArrayList<>();
        List<ItemStack> containerItems = new ArrayList<>();

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            double value = calculateTotalValue(stack);

            if (slot.inventory instanceof PlayerInventory) {
                playerValue += value;
                flattenInventory(playerItems, stack);
            } else {
                containerValue += value;
                flattenInventory(containerItems, stack);
            }
        }

        String playerText = formatValue(playerValue);
        String containerText = formatValue(containerValue);

        int screenX = screen.x;
        int screenY = screen.y;
        int width = screen.backgroundWidth;
        int height = screen.backgroundHeight;

        int playerTextWidth = client.textRenderer.getWidth(playerText);
        int playerTextX = screenX + (width / 2) - (playerTextWidth / 2);
        int playerTextY = screenY + height + 4; 

        context.drawTextWithShadow(client.textRenderer, Text.literal(playerText).formatted(Formatting.GREEN), playerTextX, playerTextY, 0xFF00FF00);

        if (mouseX >= playerTextX && mouseX <= playerTextX + playerTextWidth && mouseY >= playerTextY && mouseY <= playerTextY + 9) {
            List<Text> tooltip = getMostExpensiveItems(playerItems, client);
            context.drawTooltip(client.textRenderer, tooltip, mouseX, mouseY);
        }

        if (!(screen instanceof InventoryScreen)) {
            int containerTextWidth = client.textRenderer.getWidth(containerText);
            int containerTextX = screenX + (width / 2) - (containerTextWidth / 2);
            int containerTextY = screenY - 12; 

            context.drawTextWithShadow(client.textRenderer, Text.literal(containerText).formatted(Formatting.YELLOW), containerTextX, containerTextY, 0xFFFFFF00);

            if (mouseX >= containerTextX && mouseX <= containerTextX + containerTextWidth && mouseY >= containerTextY && mouseY <= containerTextY + 9) {
                List<Text> tooltip = getMostExpensiveItems(containerItems, client);
                context.drawTooltip(client.textRenderer, tooltip, mouseX, mouseY);
            }
        }
    }

    private static void flattenInventory(List<ItemStack> list, ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack invStack : container.stream().toList()) {
                    if (!invStack.isEmpty()) {
                        flattenInventory(list, invStack);
                    }
                }
            }
        }
        list.add(stack);
    }

    private static double calculateTotalValue(ItemStack stack) {
        double value = getSellPrice(stack) * stack.getCount();
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ContainerComponent container = stack.get(DataComponentTypes.CONTAINER);
            if (container != null) {
                for (ItemStack invStack : container.stream().toList()) {
                    if (!invStack.isEmpty()) {
                        value += calculateTotalValue(invStack);
                    }
                }
            }
        }
        return value;
    }

    private static List<Text> getMostExpensiveItems(List<ItemStack> items, MinecraftClient client) {
        record ItemGroup(ItemStack stack, int count, double value) {}
        
        Map<String, ItemGroup> groups = new HashMap<>();

        for (ItemStack stack : items) {
            double value = getSellPrice(stack) * stack.getCount();
            if (value <= 0) continue;

            String name = stack.getName().getString();
            String key = stack.getItem().toString() + "|" + name;

            if (!groups.containsKey(key)) {
                groups.put(key, new ItemGroup(stack, 0, 0.0));
            }
            ItemGroup group = groups.get(key);
            groups.put(key, new ItemGroup(group.stack(), group.count() + stack.getCount(), group.value() + value));
        }

        List<ItemGroup> sortedGroups = new ArrayList<>(groups.values());
        sortedGroups.sort((a, b) -> Double.compare(b.value(), a.value()));

        List<Text> tooltip = new ArrayList<>();
        tooltip.add(Text.literal("Top Sellable Items:").formatted(Formatting.GOLD));
        
        int count = 0;
        for (ItemGroup group : sortedGroups) {
            if (count >= 5) break;
            
            Text name = group.stack().getName().copy().formatted(Formatting.YELLOW);
            Text amount = Text.literal(" (x" + group.count() + ") = ").formatted(Formatting.GRAY);
            Text price = Text.literal("$" + formatPrice(group.value())).formatted(Formatting.GREEN);
            
            tooltip.add(Text.literal("- ").append(name).append(amount).append(price));
            count++;
        }

        if (tooltip.size() == 1) {
            tooltip.add(Text.literal("No sellable items").formatted(Formatting.GRAY));
        }

        return tooltip;
    }

    public static double getSellPrice(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
            PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (contents != null && contents.potion().isPresent()) {
                RegistryEntry<Potion> potionType = contents.potion().get();
                if (item == Items.POTION && PriceRegistry.POTION_PRICES.containsKey(potionType)) return PriceRegistry.POTION_PRICES.get(potionType).sellPrice();
                if (item == Items.SPLASH_POTION && PriceRegistry.SPLASH_POTION_PRICES.containsKey(potionType)) return PriceRegistry.SPLASH_POTION_PRICES.get(potionType).sellPrice();
                if (item == Items.LINGERING_POTION && PriceRegistry.LINGERING_POTION_PRICES.containsKey(potionType)) return PriceRegistry.LINGERING_POTION_PRICES.get(potionType).sellPrice();
                if (item == Items.TIPPED_ARROW && PriceRegistry.TIPPED_ARROW_PRICES.containsKey(potionType)) return PriceRegistry.TIPPED_ARROW_PRICES.get(potionType).sellPrice();
            }
        }

        if (PriceRegistry.ITEM_PRICES.containsKey(item)) {
            return PriceRegistry.ITEM_PRICES.get(item).sellPrice();
        }
        return 0;
    }

    public static String formatValue(double value) {
        return String.format("$%,.2f", value);
    }

    private static void addPriceLore(List<Text> lines, PriceRegistry.PriceData prices, int count) {
        if (prices.buyPrice() > 0) {
            lines.add(Text.literal("Buy: " + formatPrice(prices.buyPrice())).formatted(Formatting.GREEN));
        } else {
            lines.add(Text.literal("Cannot be bought").formatted(Formatting.GRAY));
        }
        
        if (prices.sellPrice() > 0) {
            double totalSell = prices.sellPrice() * count;
            lines.add(Text.literal("Sell: " + formatPrice(prices.sellPrice()) + " (" + formatPrice(totalSell) + ")").formatted(Formatting.RED));
        } else {
            lines.add(Text.literal("Cannot be sold").formatted(Formatting.GRAY));
        }
    }

    private static String formatPrice(double price) {
        if (price == (long) price) {
            return String.format("%,d", (long) price);
        } else {
            return String.format("%,.2f", price);
        }
    }
}