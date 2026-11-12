package com.example.ognpricetooltips;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RuneHighlighter implements ClientModInitializer {

    private static final Identifier CELESTIAL_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/celestial_paper.png");
    private static final Identifier ASTRAL_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/astral_paper.png");
    private static final Identifier OMNI_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/omni_paper.png");

    private static final Set<String> OMNI_RUNES = Set.of(
        "Bane Of Bacon", "Black Bucket", "Cubism", "Dolphin Master", "Dragon Piercing", "Dredge", "End Slayer", "Ender Veil", "Executioner", "Experience", "Extinguish", "Farmers Grace", "Field Sweep", "Gills", "Grounded", "Guardian Protector", "Guardians Wrath", "Hard Hat", "Harvest", "Incinerate", "Infinite Bucket", "Lumberjack", "Maelstrom", "Magma Walker", "Nocturnal", "Omni Omen", "Padding", "Rocket Reserve", "Roped Arrow", "Tilling", "Web Walker", "Wither Piercing"
    );

    private static final Set<String> ASTRAL_RUNES = Set.of(
        "Bane Of Elementals", "Bane Of Illager", "Botanist", "Coup de Grace", "Dual Wield", "Explosive Arrow", "Extension", "Flesh Hook", "Glide", "Heighten", "Item of Undying", "Longshot", "Low Gravity", "Mjolnir", "Nutrition", "Psi Arrows", "Seismic Slam", "Shorten", "Telekinesis", "Tunneling"
    );

    private static final Map<String, Integer> MAX_LEVELS = new HashMap<>();
    static {
        MAX_LEVELS.put("Bane Of Bacon", 5); MAX_LEVELS.put("Black Bucket", 1); MAX_LEVELS.put("Cubism", 5); MAX_LEVELS.put("Dolphin Master", 2);
        MAX_LEVELS.put("Dragon Piercing", 5); MAX_LEVELS.put("Dredge", 5); MAX_LEVELS.put("End Slayer", 5); MAX_LEVELS.put("Ender Veil", 1);
        MAX_LEVELS.put("Executioner", 5); MAX_LEVELS.put("Experience", 5); MAX_LEVELS.put("Extinguish", 10); MAX_LEVELS.put("Farmers Grace", 1);
        MAX_LEVELS.put("Field Sweep", 5); MAX_LEVELS.put("Gills", 1); MAX_LEVELS.put("Grounded", 1); MAX_LEVELS.put("Guardian Protector", 9);
        MAX_LEVELS.put("Guardians Wrath", 5); MAX_LEVELS.put("Hard Hat", 1); MAX_LEVELS.put("Harvest", 1); MAX_LEVELS.put("Incinerate", 1);
        MAX_LEVELS.put("Infinite Bucket", 1); MAX_LEVELS.put("Lumberjack", 3); MAX_LEVELS.put("Maelstrom", 3); MAX_LEVELS.put("Magma Walker", 5);
        MAX_LEVELS.put("Nocturnal", 1); MAX_LEVELS.put("Omni Omen", 5); MAX_LEVELS.put("Padding", 1); MAX_LEVELS.put("Rocket Reserve", 5);
        MAX_LEVELS.put("Roped Arrow", 5); MAX_LEVELS.put("Tilling", 3); MAX_LEVELS.put("Web Walker", 5); MAX_LEVELS.put("Wither Piercing", 5);

        MAX_LEVELS.put("Bane Of Elementals", 5); MAX_LEVELS.put("Bane Of Illager", 5); MAX_LEVELS.put("Botanist", 10); MAX_LEVELS.put("Coup de Grace", 1);
        MAX_LEVELS.put("Dual Wield", 3); MAX_LEVELS.put("Explosive Arrow", 5); MAX_LEVELS.put("Extension", 3); MAX_LEVELS.put("Flesh Hook", 5);
        MAX_LEVELS.put("Glide", 5); MAX_LEVELS.put("Heighten", 5); MAX_LEVELS.put("Item of Undying", 2); MAX_LEVELS.put("Longshot", 5);
        MAX_LEVELS.put("Low Gravity", 5); MAX_LEVELS.put("Mjolnir", 5); MAX_LEVELS.put("Nutrition", 1); MAX_LEVELS.put("Psi Arrows", 1);
        MAX_LEVELS.put("Seismic Slam", 5); MAX_LEVELS.put("Shorten", 5); MAX_LEVELS.put("Telekinesis", 1); MAX_LEVELS.put("Tunneling", 3);

        MAX_LEVELS.put("Bane Of Arthropods", 10); MAX_LEVELS.put("Blast Protection", 9); MAX_LEVELS.put("Breach", 7); MAX_LEVELS.put("Density", 6);
        MAX_LEVELS.put("Efficiency", 8); MAX_LEVELS.put("Feather Falling", 9); MAX_LEVELS.put("Fire Protection", 9); MAX_LEVELS.put("Frost Walker", 10);
        MAX_LEVELS.put("Fortune", 8); MAX_LEVELS.put("Impaling", 10); MAX_LEVELS.put("Infinity", 1); MAX_LEVELS.put("Knockback", 7);
        MAX_LEVELS.put("Looting", 8); MAX_LEVELS.put("Loyalty", 8); MAX_LEVELS.put("Piercing", 9); MAX_LEVELS.put("Power", 8);
        MAX_LEVELS.put("Projectile Protection", 9); MAX_LEVELS.put("Protection", 9); MAX_LEVELS.put("Punch", 8); MAX_LEVELS.put("Quick Charge", 5);
        MAX_LEVELS.put("Respiration", 8); MAX_LEVELS.put("Riptide", 8); MAX_LEVELS.put("Sharpness", 8); MAX_LEVELS.put("Smite", 10);
        MAX_LEVELS.put("Soul Speed", 3); MAX_LEVELS.put("Sweeping Edge", 8); MAX_LEVELS.put("Swift Sneak", 3); MAX_LEVELS.put("Thorns", 5);
        MAX_LEVELS.put("Unbreaking", 8); MAX_LEVELS.put("Wind Burst", 3);
    }

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableRuneLevelVisualizer && !ModConfig.INSTANCE.enableRuneTypeVisualizer) return;
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            
            if (client.currentScreen != null && !(client.currentScreen instanceof ChatScreen)) return;

            for (int i = 0; i < 9; i++) {
                ItemStack stack = client.player.getInventory().getStack(i);
                if (stack.isEmpty()) continue;

                int x = context.getScaledWindowWidth() / 2 - 90 + (i * 20) + 2;
                int y = context.getScaledWindowHeight() - 19;

                drawRune(context, stack, x, y, client);
            }
        });
    }

    public static void renderRuneIndicators(DrawContext context, HandledScreen<?> screen, MinecraftClient client, int mouseX, int mouseY) {
        if (!ModConfig.INSTANCE.enableRuneLevelVisualizer && !ModConfig.INSTANCE.enableRuneTypeVisualizer) return;
        if (client.player == null) return;

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            drawRune(context, stack, screen.x + slot.x, screen.y + slot.y, client);
        }

        ItemStack cursorStack = screen.getScreenHandler().getCursorStack();
        if (!cursorStack.isEmpty()) {
            drawRune(context, cursorStack, mouseX - 8, mouseY - 8, client);
        }
    }

    private static void drawRune(DrawContext context, ItemStack stack, int x, int y, MinecraftClient client) {
        if (stack.getItem() != Items.PAPER) return;
        if (stack.get(DataComponentTypes.CUSTOM_NAME) == null) return;

        String name = stack.getName().getString();
        String lowerName = name.toLowerCase();

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(MAX_LEVELS.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String runeName = entry.getKey();
            int maxLevel = entry.getValue();

            if (lowerName.contains(runeName.toLowerCase())) {
                int level = extractLevel(name);
                
                if (level > 0) {
                    if (ModConfig.INSTANCE.enableRuneTypeVisualizer) {
                        Identifier textureToDraw = CELESTIAL_TEXTURE;
                        if (OMNI_RUNES.contains(runeName)) textureToDraw = OMNI_TEXTURE;
                        else if (ASTRAL_RUNES.contains(runeName)) textureToDraw = ASTRAL_TEXTURE;

                        context.drawTexture(
                            RenderPipelines.GUI_TEXTURED, 
                            textureToDraw, 
                            x, y, 
                            0, 0, 
                            16, 16, 
                            16, 16
                        );
                    }

                    if (ModConfig.INSTANCE.enableRuneLevelVisualizer) {
                        int drawX = x + 11; 
                        int drawY = y + 9;

                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(drawX, drawY);
                        context.getMatrices().scale(0.9f, 0.9f);

                        if (level == maxLevel) {
                            context.drawText(client.textRenderer, "●", 0, 0, 0xFF00FF00, true);
                        } else if (level == maxLevel - 1) {
                            context.drawText(client.textRenderer, "●", 0, 0, 0xFFFFA500, true);
                        } else {
                            context.drawText(client.textRenderer, "●", 0, 0, 0xFFFF0000, true);
                        }
                        
                        context.getMatrices().popMatrix();
                    }
                }
                break; 
            }
        }
    }

    private static int extractLevel(String name) {
        String[] parts = name.split(" ");
        if (parts.length == 0) return 0;
        return romanToInt(parts[parts.length - 1].toUpperCase());
    }

    private static int romanToInt(String s) {
        int result = 0;
        for (int i = 0; i < s.length(); i++) {
            int current = valueOf(s.charAt(i));
            if (current == 0) return 0;
            if (i + 1 < s.length() && valueOf(s.charAt(i + 1)) > current) result -= current;
            else result += current;
        }
        return result;
    }

    private static int valueOf(char c) {
        return switch (c) {
            case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50;
            case 'C' -> 100; case 'D' -> 500; case 'M' -> 1000; default -> 0;
        };
    }
}