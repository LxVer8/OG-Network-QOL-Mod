package com.example.ognpricetooltips;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OakHavenQuestTracker implements ClientModInitializer {

    public static final int WIDTH = 250;

    public record QuestTask(String description, int current, int max, boolean completed) {}
    public record QuestCategory(String name, int tier, List<QuestTask> tasks) {}

    private static final Map<String, QuestCategory> trackedQuests = new HashMap<>();

    private static final Pattern TASK_PATTERN = Pattern.compile("Task \\d+\\s*:\\s*(.*?)\\s*([\\d,]+)/([\\d,]+)");
    private static final Pattern TIER_PATTERN = Pattern.compile("Tier (\\d+)");

    private static final Pattern TASK_DONE_PATTERN = Pattern.compile("Realm Quest\\s*\\W+\\s*You have .+?, return to (\\w+)", Pattern.CASE_INSENSITIVE);

    private static final String[] ORDER = {"boss slayer", "foraging", "merchant", "mining", "mob slayer"};

    private static final Set<String> MINING_ORES = Set.of(
        "minecraft:deepslate_lapis_ore",
        "minecraft:deepslate_redstone_ore",
        "minecraft:deepslate_emerald_ore",
        "minecraft:amethyst_block"
    );

    private static final Pattern MINE_ORES_TASK = Pattern.compile("\\bmine\\b.*\\bores?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SLAY_TASK = Pattern.compile("^\\s*slay\\b", Pattern.CASE_INSENSITIVE);

    private static final long ORE_PENDING_TIMEOUT_MS = 15_000;
    private static final long KILL_WINDOW_MS = 10_000;
    private static final int KILL_MAX_DISTANCE_SQ = 24 * 24;

    private static final Map<BlockPos, Long> pendingOres = new HashMap<>();

    private static final class AttackedMob {
        long lastHit;
        BlockPos lastPos;
    }

    private static final Map<Integer, AttackedMob> attackedMobs = new HashMap<>();

    private static final List<String> TRACKED_ITEMS = List.of(
        "Azurite", "Jade", "Ruby", "Eclipsium", "Beautyberry", "Beautyberry Jam", 
        "Beautyberry Pie", "Snail Slime", "Snail Shell", "Blob of Trash", 
        "Poisonous Vine", "Tuft of Leaves", "Silk", "Spider's Fang", 
        "Twig", "Oak Plate", "Oak Spirit's Heart", "Dry Leaf"
    );

    private static final int LINE_H = 12;
    private static final int BAR_ROW_H = 16;
    private static final int BAR_WIDTH = 150;
    private static final int DETAIL_LINE_H = 11;
    private static final int DETAIL_GAP = 4;
    private static final int DETAIL_INDENT = 8;
    private static final int DETAIL_BAR_EXTRA = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("ognpricetooltips_oakhaven_quests.json").toFile();

    @Override
    public void onInitializeClient() {
        loadQuests();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ModConfig.INSTANCE.questTrackerActive()) return;
            if (client.player == null || client.world == null) return;
            
            String currentDim = client.world.getRegistryKey().getValue().toString();
            if (!currentDim.contains("oakhaven")) return;

            if (client.world.getTime() % 10 != 0) return;

            if (client.currentScreen instanceof HandledScreen<?> screen) {
                scanScreenForQuests(client, screen);
            }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !ModConfig.INSTANCE.questTrackerActive()) return;
            String text = message.getString();
            onTaskCompletedMessage(text);
            onBossDefeatedMessage(text);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender != null || !ModConfig.INSTANCE.questTrackerActive()) return;
            onBossDefeatedMessage(message.getString());
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            onAttackBlock(pos);
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttackEntity(entity);
            return ActionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(OakHavenQuestTracker::trackMiningAndKills);

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.questTrackerActive()) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || client.world == null) return;
            if (client.currentScreen instanceof HudEditScreen) return;

            String currentDim = client.world.getRegistryKey().getValue().toString();
            if (!currentDim.contains("oakhaven")) return;

            render(context, client, ModConfig.INSTANCE.questTrackerX, ModConfig.INSTANCE.questTrackerY);
        });
    }

    private static void saveQuests() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            GSON.toJson(trackedQuests, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadQuests() {
        if (!SAVE_FILE.exists()) return;
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            Type type = new TypeToken<Map<String, QuestCategory>>() {}.getType();
            Map<String, QuestCategory> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                trackedQuests.clear();
                trackedQuests.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean inOakHaven(MinecraftClient client) {
        return client.world != null && client.world.getRegistryKey().getValue().toString().contains("oakhaven");
    }

    private static void addProgress(String catKey, Pattern taskKind) {
        QuestCategory quest = trackedQuests.get(catKey);
        if (quest == null) return;

        List<QuestTask> updated = new ArrayList<>(quest.tasks());
        for (int i = 0; i < updated.size(); i++) {
            QuestTask task = updated.get(i);
            if (task.completed()) continue;

            if (task.max() > 0 && task.current() < task.max() && taskKind.matcher(task.description()).find()) {
                updated.set(i, new QuestTask(task.description(), task.current() + 1, task.max(), false));
                trackedQuests.put(catKey, new QuestCategory(quest.name(), quest.tier(), updated));
                saveQuests();
            }
            return;
        }
    }

    private static void onBossDefeatedMessage(String message) {
        if (message.toUpperCase().contains("DEADROOT BOSS DEFEATED")) {
            addProgress("boss slayer", SLAY_TASK);
        }
    }

    private static void onAttackBlock(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ModConfig.INSTANCE.questTrackerActive() || !inOakHaven(client)) return;

        String id = Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).toString();
        if (MINING_ORES.contains(id)) {
            pendingOres.putIfAbsent(pos.toImmutable(), System.currentTimeMillis());
        }
    }

    private static void onAttackEntity(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!ModConfig.INSTANCE.questTrackerActive() || !inOakHaven(client)) return;

        if (!(entity instanceof LivingEntity) || entity.getType() == EntityType.PLAYER || entity.getType() == EntityType.ARMOR_STAND) return;

        AttackedMob mob = attackedMobs.computeIfAbsent(entity.getId(), id -> new AttackedMob());
        mob.lastHit = System.currentTimeMillis();
        mob.lastPos = entity.getBlockPos();
    }

    private static void trackMiningAndKills(MinecraftClient client) {
        if (client.world == null || client.player == null || !ModConfig.INSTANCE.questTrackerActive() || !inOakHaven(client)) {
            pendingOres.clear();
            attackedMobs.clear();
            return;
        }

        long now = System.currentTimeMillis();

        Iterator<Map.Entry<BlockPos, Long>> oreIt = pendingOres.entrySet().iterator();
        while (oreIt.hasNext()) {
            Map.Entry<BlockPos, Long> entry = oreIt.next();
            String id = Registries.BLOCK.getId(client.world.getBlockState(entry.getKey()).getBlock()).toString();
            if (!MINING_ORES.contains(id)) {
                oreIt.remove();
                addProgress("mining", MINE_ORES_TASK);
            } else if (now - entry.getValue() > ORE_PENDING_TIMEOUT_MS) {
                oreIt.remove();
            }
        }

        Iterator<Map.Entry<Integer, AttackedMob>> mobIt = attackedMobs.entrySet().iterator();
        while (mobIt.hasNext()) {
            Map.Entry<Integer, AttackedMob> entry = mobIt.next();
            AttackedMob mob = entry.getValue();
            Entity entity = client.world.getEntityById(entry.getKey());

            boolean kill = false;
            boolean done = false;
            if (entity == null) {
                kill = now - mob.lastHit <= KILL_WINDOW_MS
                        && client.player.getBlockPos().getSquaredDistance(mob.lastPos) <= KILL_MAX_DISTANCE_SQ;
                done = true;
            } else if (entity instanceof LivingEntity living && living.isDead()) {
                kill = true;
                done = true;
            } else {
                mob.lastPos = entity.getBlockPos();
                if (now - mob.lastHit > 2 * KILL_WINDOW_MS) done = true;
            }

            if (kill) addProgress("mob slayer", SLAY_TASK);
            if (done) mobIt.remove();
        }
    }

    private static void onTaskCompletedMessage(String message) {
        Matcher matcher = TASK_DONE_PATTERN.matcher(message);
        if (!matcher.find()) return;

        String catKey = categoryForNpc(matcher.group(1));
        if (catKey == null) return;

        QuestCategory quest = trackedQuests.get(catKey);
        if (quest == null) return;

        List<QuestTask> updated = new ArrayList<>(quest.tasks());
        for (int i = 0; i < updated.size(); i++) {
            QuestTask task = updated.get(i);
            if (!task.completed()) {
                int count = task.max() > 0 ? task.max() : task.current();
                updated.set(i, new QuestTask(task.description(), count, task.max(), true));
                break;
            }
        }
        trackedQuests.put(catKey, new QuestCategory(quest.name(), quest.tier(), updated));
        saveQuests();
    }

    private static String categoryForNpc(String npcName) {
        return switch (npcName.toLowerCase()) {
            case "alice" -> "foraging";
            case "lily" -> "merchant";
            case "trent" -> "mining";
            case "cassie" -> "mob slayer";
            case "jack" -> "boss slayer";
            default -> null;
        };
    }

    private void scanScreenForQuests(MinecraftClient client, HandledScreen<?> screen) {
        String title = screen.getTitle().getString().toLowerCase();
        String catKey = null;
        
        if (title.contains("foraging")) catKey = "foraging";
        else if (title.contains("merchant")) catKey = "merchant";
        else if (title.contains("mining")) catKey = "mining";
        else if (title.contains("mob slayer")) catKey = "mob slayer";
        else if (title.contains("boss slayer")) catKey = "boss slayer";

        if (catKey == null) return;

        int tier = 0;
        List<QuestTask> tasks = new ArrayList<>();
        boolean foundActive = false;

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            if (!stack.getItem().toString().contains("compass")) continue;

            List<Text> tooltip = stack.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
            
            boolean isActive = false;
            for (Text text : tooltip) {
                if (text.getString().contains("In Progress")) {
                    isActive = true;
                    break;
                }
            }
            
            if (!isActive) continue;

            foundActive = true;
            for (Text text : tooltip) {
                String line = text.getString().trim();
                
                Matcher tierMatcher = TIER_PATTERN.matcher(line);
                if (tierMatcher.find()) {
                    tier = Integer.parseInt(tierMatcher.group(1));
                }

                Matcher taskMatcher = TASK_PATTERN.matcher(line);
                if (taskMatcher.find()) {
                    String desc = taskMatcher.group(1).trim();
                    int current = Integer.parseInt(taskMatcher.group(2).replace(",", ""));
                    int max = Integer.parseInt(taskMatcher.group(3).replace(",", ""));
                    
                    boolean hasCheck = line.contains("✔") || line.contains("✓") || line.contains("✅");
                    boolean hasX = line.contains("✖") || line.contains("❌");
                    
                    boolean completed = hasCheck || (!hasX && current >= max);
                    
                    tasks.add(new QuestTask(desc, current, max, completed));
                }
                else if (line.contains("✔") || line.contains("✓") || line.contains("✅")) {
                    tasks.add(new QuestTask(line, 0, 0, true));
                }
            }
            
            break; 
        }

        if (foundActive && !tasks.isEmpty()) {
            trackedQuests.put(catKey, new QuestCategory(capitalize(catKey), tier, tasks));
            saveQuests();
        }
    }

    private static ModConfig.QuestStyle style() {
        ModConfig.QuestStyle style = ModConfig.INSTANCE.questStyle;
        return style == null ? ModConfig.QuestStyle.COMPACT : style;
    }

    private static boolean hasData(QuestCategory quest) {
        return quest != null && !quest.tasks().isEmpty();
    }

    private static QuestTask firstOpenTask(QuestCategory quest) {
        for (QuestTask task : quest.tasks()) {
            if (!task.completed()) return task;
        }
        return null;
    }

    private static boolean hasBar(QuestCategory quest) {
        QuestTask open = firstOpenTask(quest);
        return open != null && open.max() > 0;
    }

    private static boolean isQuestlineEnabled(String catKey) {
        return switch (catKey) {
            case "boss slayer" -> ModConfig.INSTANCE.enableQuestBossSlayer;
            case "foraging" -> ModConfig.INSTANCE.enableQuestForaging;
            case "merchant" -> ModConfig.INSTANCE.enableQuestMerchant;
            case "mining" -> ModConfig.INSTANCE.enableQuestMining;
            case "mob slayer" -> ModConfig.INSTANCE.enableQuestMobSlayer;
            default -> false;
        };
    }

    private static int blockHeight(String catKey) {
        if (!isQuestlineEnabled(catKey)) return 0;

        QuestCategory quest = trackedQuests.get(catKey);
        if (!hasData(quest)) return LINE_H;

        return switch (style()) {
            case DETAILED -> DETAIL_LINE_H * (1 + quest.tasks().size()) + DETAIL_GAP;
            case DETAILED_BARS -> DETAIL_LINE_H * (1 + quest.tasks().size()) + DETAIL_GAP + (hasBar(quest) ? DETAIL_BAR_EXTRA : 0);
            case BARS -> hasBar(quest) ? BAR_ROW_H : LINE_H;
            case COMPACT -> LINE_H;
        };
    }

    public static int getHeight() {
        int height = 0;
        for (String catKey : ORDER) {
            height += blockHeight(catKey);
        }
        return height;
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY) {
        int y = startY;
        ModConfig.QuestStyle style = style();

        for (String catKey : ORDER) {
            if (!isQuestlineEnabled(catKey)) continue;

            QuestCategory quest = trackedQuests.get(catKey);
            String displayName = capitalize(catKey);
            Formatting catColor = getCategoryColor(catKey);

            if (!hasData(quest)) {
                drawOutlined(context, client.textRenderer, Text.literal(displayName + ": ").formatted(catColor), startX, y, 0xFFFFFFFF);
                int nameWidth = client.textRenderer.getWidth(displayName + ": ");
                drawOutlined(context, client.textRenderer, Text.literal("Open the menu of this NPC to track the quest").formatted(Formatting.DARK_GRAY), startX + nameWidth, y, 0xFFFFFFFF);
            } else if (style == ModConfig.QuestStyle.DETAILED || style == ModConfig.QuestStyle.DETAILED_BARS) {
                renderDetailed(context, client, quest, displayName, catColor, startX, y, style == ModConfig.QuestStyle.DETAILED_BARS);
            } else {
                renderSingleLine(context, client, quest, displayName, catColor, startX, y, style == ModConfig.QuestStyle.BARS);
            }

            y += blockHeight(catKey);
        }
    }

    private static void renderSingleLine(DrawContext context, MinecraftClient client, QuestCategory quest,
                                         String displayName, Formatting catColor, int x, int y, boolean withBar) {
        drawOutlined(context, client.textRenderer, Text.literal(displayName + ": ").formatted(catColor), x, y, 0xFFFFFFFF);
        int nameWidth = client.textRenderer.getWidth(displayName + ": ");

        QuestTask active = firstOpenTask(quest);
        if (active == null) {
            drawOutlined(context, client.textRenderer, Text.literal("Return to the NPC").formatted(Formatting.GREEN), x + nameWidth, y, 0xFFFFFFFF);
            return;
        }

        int count = shownCount(active, client);
        int max = active.max();

        String taskText = active.description();
        if (max > 0) {
            taskText += " " + count + "/" + max;
        }

        drawOutlined(context, client.textRenderer, Text.literal(taskText).formatted(Formatting.WHITE), x + nameWidth, y, 0xFFFFFFFF);

        if (withBar && max > 0) {
            drawBar(context, x, y + 10, BAR_WIDTH, count, max);
        }
    }

    private static void renderDetailed(DrawContext context, MinecraftClient client, QuestCategory quest,
                                       String displayName, Formatting catColor, int x, int y, boolean withBar) {
        QuestTask open = firstOpenTask(quest);

        String headerText = quest.tier() > 0 ? displayName + " - Tier " + quest.tier() : displayName;
        Text header = Text.empty().append(Text.literal(headerText).formatted(catColor));
        if (open == null) {
            header = Text.empty().append(Text.literal(headerText + "  ").formatted(catColor))
                    .append(Text.literal("Return to the NPC").formatted(Formatting.GREEN));
        }
        drawOutlined(context, client.textRenderer, header, x, y, 0xFFFFFFFF);

        int lineY = y + DETAIL_LINE_H;
        for (QuestTask task : quest.tasks()) {
            String marker;
            Formatting markerColor;
            Formatting color;
            int count;

            if (task.completed()) {
                marker = "✔ ";
                markerColor = Formatting.GREEN;
                color = Formatting.GRAY;
                count = task.max() > 0 ? task.max() : task.current();
            } else {
                marker = "- ";
                markerColor = Formatting.WHITE;
                color = Formatting.WHITE;
                count = task == open ? shownCount(task, client) : task.current();
            }

            String description = task.description().replaceFirst("^[✔✓✅]\\s*", "");
            if (task.max() > 0) {
                description += " " + count + "/" + task.max();
            }

            Text line = Text.empty()
                    .append(Text.literal(marker).formatted(markerColor))
                    .append(Text.literal(description).formatted(color));
            drawOutlined(context, client.textRenderer, line, x + DETAIL_INDENT, lineY, 0xFFFFFFFF);

            if (withBar && task == open && task.max() > 0) {
                drawBar(context, x + DETAIL_INDENT, lineY + 10, BAR_WIDTH, count, task.max());
                lineY += DETAIL_BAR_EXTRA;
            }
            lineY += DETAIL_LINE_H;
        }
    }

    private static void drawOutlined(DrawContext context, TextRenderer renderer, Text text, int x, int y, int color) {
        Text outlineText = Text.literal(text.getString()).formatted(Formatting.BLACK);

        context.drawText(renderer, outlineText, x - 1, y - 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x, y - 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y - 1, 0xFFFFFFFF, false);

        context.drawText(renderer, outlineText, x - 1, y, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y, 0xFFFFFFFF, false);

        context.drawText(renderer, outlineText, x - 1, y + 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x, y + 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y + 1, 0xFFFFFFFF, false);

        context.drawTextWithShadow(renderer, text, x, y, color);
    }

    private static void drawBar(DrawContext context, int x, int y, int width, int current, int max) {
        context.fill(x - 1, y - 1, x + width + 1, y + 3, 0xFF000000);
        context.fill(x, y, x + width, y + 2, 0xFF3A3A3A);

        int filled = Math.min(width, Math.round(width * (current / (float) max)));
        int color = current >= max ? 0xFF55FF55 : (current * 2 >= max ? 0xFFFFFF55 : 0xFFFFFFFF);
        if (filled > 0) {
            context.fill(x, y, x + filled, y + 2, color);
        }
    }

    private static int shownCount(QuestTask task, MinecraftClient client) {
        int current = task.current();
        int max = task.max();

        if (task.completed()) return max > 0 ? max : current;

        if (max > 0 && !task.description().trim().toLowerCase().startsWith("sell")) {
            for (String trackedItem : TRACKED_ITEMS) {
                if (task.description().toLowerCase().contains(trackedItem.toLowerCase())) {
                    current += getInventoryCount(trackedItem, client);
                    break;
                }
            }
            if (current > max) current = max;
        }
        return current;
    }

    private static int getInventoryCount(String itemName, MinecraftClient client) {
        int count = 0;
        if (client.player == null) return 0;

        var inventory = client.player.getInventory();
        
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && stack.getName().getString().equalsIgnoreCase(itemName)) {
                count += stack.getCount();
            }
        }

        return count;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        String[] words = str.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(" ");
            }
        }
        return sb.toString().trim();
    }

    private static Formatting getCategoryColor(String name) {
        return switch (name.toLowerCase()) {
            case "foraging" -> Formatting.GREEN;
            case "merchant" -> Formatting.YELLOW;
            case "mining" -> Formatting.GOLD;
            case "mob slayer" -> Formatting.RED;
            case "boss slayer" -> Formatting.DARK_RED;
            default -> Formatting.WHITE;
        };
    }
}