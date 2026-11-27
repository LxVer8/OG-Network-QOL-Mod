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
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RuneHighlighter implements ClientModInitializer {

    private static final Identifier COMMON_TEXTURE = Identifier.of("minecraft", "textures/item/paper.png");
    private static final Identifier RARE_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/rare_paper.png");
    private static final Identifier LEGENDARY_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/legendary_paper.png");
    private static final Identifier CELESTIAL_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/celestial_paper.png");
    private static final Identifier OMNI_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/omni_paper.png");
    private static final Identifier ASTRAL_TEXTURE = Identifier.of("ognpricetooltips", "textures/item/astral_paper.png");

    public static final Set<String> OMNI_RUNES = Set.of(
        "Bane Of Bacon", "Black Bucket", "Cubism", "Dolphin Master", "Dragon Piercing", "Dredge", "End Slayer", "Ender Veil", "Executioner", "Experience", "Extinguish", "Farmers Grace", "Field Sweep", "Gills", "Grounded", "Guardian Protection", "Guardians Wrath", "Hard Hat", "Harvest", "Incinerate", "Infinite Bucket", "Lumberjack", "Maelstrom", "Magma Walker", "Nocturnal", "Omni Omen", "Padding", "Rocket Reserve", "Roped Arrow", "Tilling", "Web Walker", "Wither Piercing"
    );

    public static final Set<String> ASTRAL_RUNES = Set.of(
        "Bane Of Elementals", "Bane Of Illager", "Botanist", "Coup de Grace", "Dual Wield", "Explosive Arrow", "Extension", "Flesh Hook", "Glide", "Heighten", "Item of Undying", "Longshot", "Low Gravity", "Mjolnir", "Nutrition", "Psi Arrows", "Seismic Slam", "Shorten", "Telekinesis", "Tunneling"
    );

    public static final Map<String, Integer> MAX_LEVELS = new HashMap<>();
    static {
        MAX_LEVELS.put("Bane Of Bacon", 5); MAX_LEVELS.put("Black Bucket", 1); MAX_LEVELS.put("Cubism", 5); MAX_LEVELS.put("Dolphin Master", 2);
        MAX_LEVELS.put("Dragon Piercing", 5); MAX_LEVELS.put("Dredge", 5); MAX_LEVELS.put("End Slayer", 5); MAX_LEVELS.put("Ender Veil", 1);
        MAX_LEVELS.put("Executioner", 5); MAX_LEVELS.put("Experience", 5); MAX_LEVELS.put("Extinguish", 10); MAX_LEVELS.put("Farmers Grace", 1);
        MAX_LEVELS.put("Field Sweep", 5); MAX_LEVELS.put("Gills", 1); MAX_LEVELS.put("Grounded", 1); MAX_LEVELS.put("Guardian Protection", 9);
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
        MAX_LEVELS.put("Efficiency", 8); MAX_LEVELS.put("Feather Falling", 9); MAX_LEVELS.put("Fire Aspect", 2); MAX_LEVELS.put("Fire Protection", 9); MAX_LEVELS.put("Frost Walker", 10);
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
            context.drawItem(cursorStack, mouseX - 8, mouseY - 8);
            drawRune(context, cursorStack, mouseX - 8, mouseY - 8, client);
            context.drawStackOverlay(client.textRenderer, cursorStack, mouseX - 8, mouseY - 8);
        }
    }

    private static void drawRune(DrawContext context, ItemStack stack, int x, int y, MinecraftClient client) {
        if (stack.getItem() != Items.PAPER) return;
        if (stack.get(DataComponentTypes.CUSTOM_NAME) == null) return;

        String name = stack.getName().getString();
        String lowerName = normalize(name);

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(MAX_LEVELS.entrySet());
        sortedEntries.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));

        for (Map.Entry<String, Integer> entry : sortedEntries) {
            String runeName = entry.getKey();
            int maxLevel = entry.getValue();

            if (lowerName.contains(normalize(runeName))) {
                int level = extractLevel(name);
                
                if (level > 0) {
                    if (ModConfig.INSTANCE.enableRuneTypeVisualizer) {
                        Identifier textureToDraw = getRuneTexture(runeName, level);
                        if (!textureToDraw.equals(COMMON_TEXTURE)) {
                            context.drawTexture(
                                RenderPipelines.GUI_TEXTURED, 
                                textureToDraw, 
                                x, y, 
                                0, 0, 
                                16, 16, 
                                16, 16
                            );
                        }
                    }

                    if (ModConfig.INSTANCE.enableRuneLevelVisualizer) {
                        int drawX = x + 11; 
                        int drawY = y + 1;

                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(drawX, drawY);
                        context.getMatrices().scale(0.9f, 0.9f);

                        if (level > maxLevel) {
                            context.drawText(client.textRenderer, "●", 0, 0, 0xFFAA00AA, true);
                        } else if (level == maxLevel) {
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

    public static int extractLevel(String name) {
        String[] parts = name.split(" ");
        if (parts.length == 0) return 0;
        String lastPart = parts[parts.length - 1];
        try {
            return Integer.parseInt(lastPart);
        } catch (NumberFormatException e) {
            return romanToInt(lastPart.toUpperCase());
        }
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

    public static String getRuneTier(String name, int level) {
        String norm = normalize(name);

        if (norm.equals("guardian protection") || norm.equals("guardian protector")) {
            return "OMNI";
        }

        for (String omni : OMNI_RUNES) {
            if (normalize(omni).equals(norm)) return "OMNI";
        }
        for (String astral : ASTRAL_RUNES) {
            if (normalize(astral).equals(norm)) return "ASTRAL";
        }

        return switch (norm) {
            case "bane of arthropods" -> level >= 8 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : level >= 5 ? "RARE" : "COMMON";
            case "blast protection" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 4 ? "RARE" : "COMMON";
            case "breach" -> level >= 3 ? "CELESTIAL" : "COMMON";
            case "density" -> level >= 4 ? "CELESTIAL" : "COMMON";
            case "efficiency" -> level >= 7 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : level >= 5 ? "RARE" : "COMMON";
            case "feather falling" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 4 ? "RARE" : "COMMON";
            case "fire protection" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 4 ? "RARE" : "COMMON";
            case "fortune" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "frost walker" -> level >= 6 ? "CELESTIAL" : level >= 3 ? "LEGENDARY" : level >= 2 ? "RARE" : "COMMON";
            case "impaling" -> level >= 8 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : level >= 5 ? "RARE" : "COMMON";
            case "infinity" -> level >= 1 ? "CELESTIAL" : "COMMON";
            case "knockback" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "looting" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "loyalty" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "piercing" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "power" -> level >= 7 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : "COMMON";
            case "projectile protection" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 4 ? "RARE" : "COMMON";
            case "protection" -> level >= 7 ? "CELESTIAL" : level >= 5 ? "LEGENDARY" : level >= 4 ? "RARE" : "COMMON";
            case "punch" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "quick charge" -> level >= 4 ? "CELESTIAL" : level >= 3 ? "LEGENDARY" : "COMMON";
            case "respiration" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "riptide" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "sharpness" -> level >= 7 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : level >= 5 ? "RARE" : "COMMON";
            case "smite" -> level >= 8 ? "CELESTIAL" : level >= 6 ? "LEGENDARY" : level >= 5 ? "RARE" : "COMMON";
            case "soul speed" -> level >= 2 ? "CELESTIAL" : "COMMON";
            case "sweeping edge" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "swift sneak" -> level >= 1 ? "CELESTIAL" : "COMMON";
            case "thorns" -> level >= 2 ? "CELESTIAL" : "COMMON";
            case "unbreaking" -> level >= 6 ? "CELESTIAL" : level >= 4 ? "LEGENDARY" : level >= 3 ? "RARE" : "COMMON";
            case "wind burst" -> level >= 1 ? "CELESTIAL" : "COMMON";
            default -> "COMMON";
        };
    }

    public static Formatting getRuneColor(String name, int level) {
        String tier = getRuneTier(name, level);
        return switch (tier) {
            case "OMNI" -> Formatting.LIGHT_PURPLE;
            case "ASTRAL" -> Formatting.BLUE;
            case "CELESTIAL" -> Formatting.RED;
            case "LEGENDARY" -> Formatting.GOLD;
            case "RARE" -> Formatting.AQUA;
            default -> null;
        };
    }

    public static Identifier getRuneTexture(String name, int level) {
        String tier = getRuneTier(name, level);
        return switch (tier) {
            case "OMNI" -> OMNI_TEXTURE;
            case "ASTRAL" -> ASTRAL_TEXTURE;
            case "CELESTIAL" -> CELESTIAL_TEXTURE;
            case "LEGENDARY" -> LEGENDARY_TEXTURE;
            case "RARE" -> RARE_TEXTURE;
            default -> COMMON_TEXTURE;
        };
    }

    private static String normalize(String input) {
        if (input == null) return "";
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}