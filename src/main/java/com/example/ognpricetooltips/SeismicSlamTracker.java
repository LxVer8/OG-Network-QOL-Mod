package com.example.ognpricetooltips;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SeismicSlamTracker implements ClientModInitializer {

    public static final int WIDTH = 120;

    private static final long DEFAULT_DURATION_MS = 8000;

    private static final long REPEAT_GAP_MS = 2000;

    private static final long HOLD_MS = 4000;
    private static final long FADE_MS = 2000;

    private static boolean DEBUG = true;
    private static final int DEBUG_MAX_LINES = 60;
    private static final Set<String> debugSeen = new HashSet<>();

    private static final Pattern LEVEL_PATTERN = Pattern.compile(
            "seismic slam\\s*:?\\s*(?:enchantment\\.level\\.(\\d+)|(\\d+)|([ivxlc]+)(?![a-z]))",
            Pattern.CASE_INSENSITIVE);

    private static long endTime = 0;
    private static long lastSeen = 0;
    private static long lastActive = 0;

    private static Field[] hudTextFields = null;
    private static Object[] hudLastTexts = null;

    @Override
    public void onInitializeClient() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleMessage(message, overlay, "event"));

        ClientTickEvents.END_CLIENT_TICK.register(SeismicSlamTracker::pollHud);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableSeismicSlamTimer) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            render(context, client, ModConfig.INSTANCE.seismicSlamX, ModConfig.INSTANCE.seismicSlamY,
                    ModConfig.INSTANCE.seismicSlamRightAligned, false);
        });
    }

    private static void pollHud(MinecraftClient client) {
        if (client.player == null || client.inGameHud == null) return;
        InGameHud hud = client.inGameHud;

        try {
            if (hudTextFields == null) {
                List<Field> found = new ArrayList<>();
                for (Field f : InGameHud.class.getDeclaredFields()) {
                    if (f.getType() == Text.class && !Modifier.isStatic(f.getModifiers())) {
                        f.setAccessible(true);
                        found.add(f);
                    }
                }
                hudTextFields = found.toArray(new Field[0]);
                hudLastTexts = new Object[hudTextFields.length];
                for (int i = 0; i < hudTextFields.length; i++) {
                    hudLastTexts[i] = hudTextFields[i].get(hud);
                }
                if (DEBUG) System.out.println("[SeismicSlamTracker] watching " + hudTextFields.length + " HUD text fields");
                return;
            }

            for (int i = 0; i < hudTextFields.length; i++) {
                Object value = hudTextFields[i].get(hud);
                if (value != hudLastTexts[i]) {
                    hudLastTexts[i] = value;
                    if (value instanceof Text text) handleMessage(text, true, "hud:" + hudTextFields[i].getName());
                }
            }
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
            hudTextFields = new Field[0];
            hudLastTexts = new Object[0];
        }
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= 32 && c < 127) sb.append(c);
            else sb.append(String.format("\\u%04X", (int) c));
        }
        return sb.toString();
    }

    private static void handleMessage(Text message, boolean overlay, String source) {
        String raw = message.getString();
        String letters = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        if (DEBUG && debugSeen.size() < DEBUG_MAX_LINES && debugSeen.add(source + "|" + raw)) {
            System.out.println("[SeismicSlamTracker] text (" + source + ", overlay=" + overlay + "): \"" + escape(raw) + "\"");
        }

        if (!ModConfig.INSTANCE.enableSeismicSlamTimer) return;

        boolean primed = letters.contains("seismicslamprimed");
        boolean readyIn = letters.contains("seismicslamreadyin");
        if (!primed && !readyIn) return;

        long now = System.currentTimeMillis();

        if (readyIn && !primed && endTime > now) return;

        if (now - lastSeen <= REPEAT_GAP_MS) return;

        lastSeen = now;

        int level = -1;
        try {
            level = findSeismicSlamLevel(MinecraftClient.getInstance());
        } catch (Exception e) {
            if (DEBUG) e.printStackTrace();
        }

        long duration = level > 0 ? durationForLevel(level) : DEFAULT_DURATION_MS;
        endTime = now + duration;

        if (DEBUG) {
            System.out.println("[SeismicSlamTracker] TRIGGERED via " + source + ": level=" + level + " duration=" + duration + "ms");
        }
    }

    private static long durationForLevel(int level) {
        int clamped = Math.max(1, Math.min(6, level));
        return (13 - clamped) * 1000L;
    }

    private static boolean isHoldingMace(MinecraftClient client) {
        if (client.player == null) return false;
        return client.player.getMainHandStack().isOf(Items.MACE) || client.player.getOffHandStack().isOf(Items.MACE);
    }

    private static int findSeismicSlamLevel(MinecraftClient client) {
        if (client.player == null) return -1;

        ItemStack[] hands = { client.player.getMainHandStack(), client.player.getOffHandStack() };
        for (ItemStack stack : hands) {
            int level = levelOf(client, stack);
            if (level > 0) return level;
        }
        return -1;
    }

    private static int levelOf(MinecraftClient client, ItemStack stack) {
        if (stack.isEmpty()) return -1;

        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            Optional<RegistryKey<Enchantment>> key = entry.getKey().getKey();
            if (key.isPresent() && key.get().getValue().getPath().contains("seismic")) {
                return entry.getIntValue();
            }
        }

        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
        for (Text line : tooltip) {
            int level = parseLevel(line.getString());
            if (level > 0) return level;
        }
        return -1;
    }

    private static int parseLevel(String line) {
        Matcher m = LEVEL_PATTERN.matcher(line);
        if (m.find()) {
            if (m.group(1) != null) return Integer.parseInt(m.group(1));
            if (m.group(2) != null) return Integer.parseInt(m.group(2));
            if (m.group(3) != null) return romanToInt(m.group(3));
        }
        if (line.trim().equalsIgnoreCase("seismic slam")) return 1;
        return -1;
    }

    private static int romanToInt(String s) {
        int total = 0;
        int prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (Character.toUpperCase(s.charAt(i))) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                default -> 0;
            };
            if (v < prev) {
                total -= v;
            } else {
                total += v;
                prev = v;
            }
        }
        return total;
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY, boolean rightAligned, boolean preview) {
        long now = System.currentTimeMillis();
        long left = endTime - now;
        boolean counting = left > 0;

        float alpha = 1f;
        if (!preview) {
            if (counting || isHoldingMace(client)) {
                lastActive = now;
            } else if (endTime > lastActive) {
                lastActive = Math.min(endTime, now);
            }

            long since = now - lastActive;
            if (since > HOLD_MS + FADE_MS) return;
            if (since > HOLD_MS) alpha = 1f - (float) (since - HOLD_MS) / FADE_MS;
        }

        Text value = counting
                ? Text.literal(String.format(Locale.ROOT, "%.1fs", left / 1000.0)).formatted(Formatting.RED)
                : Text.literal("Ready").formatted(Formatting.GREEN);

        Text text = Text.empty()
                .append(Text.literal("Seismic Slam: ").formatted(Formatting.YELLOW))
                .append(value);

        drawText(context, client, text, startX, startY, rightAligned, alpha);
    }

    private static void drawText(DrawContext context, MinecraftClient client, Text text, int boxX, int y, boolean rightAligned, float alpha) {
        int a = Math.round(alpha * 255);
        int color = (a << 24) | 0xFFFFFF;

        int x = rightAligned ? boxX + WIDTH - client.textRenderer.getWidth(text) : boxX;

        Text outlineText = Text.literal(text.getString()).formatted(Formatting.BLACK);

        context.drawText(client.textRenderer, outlineText, x - 1, y - 1, color, false);
        context.drawText(client.textRenderer, outlineText, x, y - 1, color, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y - 1, color, false);

        context.drawText(client.textRenderer, outlineText, x - 1, y, color, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y, color, false);

        context.drawText(client.textRenderer, outlineText, x - 1, y + 1, color, false);
        context.drawText(client.textRenderer, outlineText, x, y + 1, color, false);
        context.drawText(client.textRenderer, outlineText, x + 1, y + 1, color, false);

        context.drawTextWithShadow(client.textRenderer, text, x, y, color);
    }
}