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
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.BrewingStandScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DailyQuestTracker implements ClientModInitializer {

    public static final int WIDTH = 250;

    private static final class DailyQuest {
        final String title;
        final String display;
        final String verb;
        final String target;
        final String dimension;
        final int max;
        final long expiresAt;
        int current;

        DailyQuest(String title, String display, String verb, String target, String dimension,
                   int current, int max, long expiresAt) {
            this.title = title;
            this.display = display;
            this.verb = verb;
            this.target = target;
            this.dimension = dimension;
            this.current = current;
            this.max = max;
            this.expiresAt = expiresAt;
        }
    }

    private record PendingBlock(String path, long time) {}

    private static final class AttackedMob {
        long lastHit;
        BlockPos lastPos;
        String type;
    }

    private static final String MENU_TITLE = "daily quests";

    private static final Pattern PROGRESS_PATTERN = Pattern.compile("Progress:\\s*(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("^(\\w+)\\s+(\\d[\\d,]*)\\s+(.+?)\\.?$");
    private static final Pattern DIMENSION_PATTERN = Pattern.compile("^(.*?)\\s+in\\s+(?:the\\s+)?(overworld|nether|end)(?:\\s+dimension)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOSS_BAR_PATTERN = Pattern.compile("^(.+?)\\s+(\\d[\\d,]*)\\s*/\\s*(\\d[\\d,]*)");
    private static final Pattern KEY_PATTERN = Pattern.compile("virtual\\s+(\\w+)\\s+dungeon\\s+crate\\s+key", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIFFICULTY_PATTERN = Pattern.compile("\\b(easy|medium|hard|nightm\\w+)\\b", Pattern.CASE_INSENSITIVE);

    private static final Set<String> BLOCK_VERBS = Set.of("mine", "break", "chop", "dig");
    private static final Set<String> KILL_VERBS = Set.of("kill", "slay", "defeat", "hunt", "eliminate");
    private static final Set<String> FURNACE_VERBS = Set.of("smelt", "cook");
    private static final Set<String> ANY_MOB = Set.of("mob", "monster", "hostilemob", "hostile", "entity", "creature");
    private static final Set<String> SMALL_WORDS = Set.of("in", "the", "of", "a", "an");
    private static final Set<String> PLAIN_POTIONS = Set.of("water", "mundane", "thick", "awkward");

    private static final long PENDING_BLOCK_TIMEOUT_MS = 15_000;
    private static final long KILL_WINDOW_MS = 10_000;
    private static final int KILL_MAX_DISTANCE_SQ = 24 * 24;
    private static final long PAIR_WINDOW_MS = 15_000;

    private static final int LINE_H = 12;
    private static final int BAR_ROW_H = 16;
    private static final int BAR_WIDTH = 150;

    private static final Map<Integer, DailyQuest> quests = new TreeMap<>();
    private static final Map<BlockPos, PendingBlock> pendingBlocks = new HashMap<>();
    private static final Map<Integer, AttackedMob> attackedMobs = new HashMap<>();

    private static final Map<String, String> lastBossBars = new HashMap<>();
    private static Field bossBarsField = null;
    private static boolean bossBarsLookedUp = false;

    private static ItemStack lastFurnaceOutput = ItemStack.EMPTY;
    private static final ItemStack[] lastPotions = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};

    private static long winTime = 0;
    private static long keyTime = 0;
    private static String winDifficulty = null;
    private static String keyDifficulty = null;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File SAVE_FILE = FabricLoader.getInstance().getConfigDir().resolve("ognpricetooltips_daily_quests.json").toFile();

    @Override
    public void onInitializeClient() {
        loadQuests();
        
        ClientTickEvents.END_CLIENT_TICK.register(DailyQuestTracker::tick);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            onMessage(message);
        });

        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
            if (sender != null) return;
            onMessage(message);
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            onAttackBlock(pos);
            return ActionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            onAttackEntity(entity);
            return ActionResult.PASS;
        });

        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!ModConfig.INSTANCE.enableDailyQuestTracker) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden || client.world == null) return;
            if (client.currentScreen instanceof HudEditScreen) return;
            if (!inTrackedDimension(client)) return;

            render(context, client, ModConfig.INSTANCE.dailyQuestX, ModConfig.INSTANCE.dailyQuestY);
        });
    }

    private static void saveQuests() {
        try (FileWriter writer = new FileWriter(SAVE_FILE)) {
            GSON.toJson(quests, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void loadQuests() {
        if (!SAVE_FILE.exists()) return;
        try (FileReader reader = new FileReader(SAVE_FILE)) {
            Type type = new TypeToken<TreeMap<Integer, DailyQuest>>() {}.getType();
            TreeMap<Integer, DailyQuest> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                quests.clear();
                quests.putAll(loaded);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean inTrackedDimension(MinecraftClient client) {
        return client.world != null && !client.world.getRegistryKey().getValue().toString().contains("oakhaven");
    }

    private static boolean tracking(MinecraftClient client) {
        return ModConfig.INSTANCE.enableDailyQuestTracker && client.player != null && client.world != null
                && inTrackedDimension(client);
    }

    private static String dimensionPath(MinecraftClient client) {
        return client.world.getRegistryKey().getValue().getPath();
    }

    private static void tick(MinecraftClient client) {
        if (!ModConfig.INSTANCE.enableDailyQuestTracker || client.player == null || client.world == null) {
            resetTransient();
            return;
        }

        long now = System.currentTimeMillis();
        
        boolean removed = quests.values().removeIf(q -> q.expiresAt <= now);
        if (removed) saveQuests();

        if (client.currentScreen instanceof HandledScreen<?> screen
                && client.world.getTime() % 10 == 0
                && screen.getTitle().getString().toLowerCase(Locale.ROOT).contains(MENU_TITLE)) {
            scanScreen(screen);
        }

        if (!tracking(client)) {
            resetTransient();
            return;
        }

        trackOutputs(client);
        trackBlocks(client, now);
        trackKills(client, now);
        trackBossBars(client);
    }

    private static void resetTransient() {
        pendingBlocks.clear();
        attackedMobs.clear();
        lastBossBars.clear();
        lastFurnaceOutput = ItemStack.EMPTY;
        for (int i = 0; i < lastPotions.length; i++) lastPotions[i] = ItemStack.EMPTY;
    }

    private static long nextReset() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime reset = now.withHour(10).withMinute(0).withSecond(0).withNano(0);
        if (!now.isBefore(reset)) reset = reset.plusDays(1);
        return reset.toInstant().toEpochMilli();
    }

    private static void scanScreen(HandledScreen<?> screen) {
        long expiresAt = nextReset();
        List<Slot> slots = screen.getScreenHandler().slots;
        boolean changed = false;

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory instanceof PlayerInventory) continue;

            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            DailyQuest quest = parseQuest(stack, expiresAt);
            if (quest != null) {
                quests.put(i, quest);
                changed = true;
            } else {
                if (quests.remove(i) != null) changed = true;
            }
        }
        if (changed) saveQuests();
    }

    private static DailyQuest parseQuest(ItemStack stack, long expiresAt) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) return null;

        int current = 0;
        int max = -1;
        String description = null;

        for (Text line : lore.lines()) {
            String text = line.getString().trim();
            Matcher progress = PROGRESS_PATTERN.matcher(text);
            if (progress.find()) {
                current = parseNumber(progress.group(1));
                max = parseNumber(progress.group(2));
            } else if (description == null && !text.isEmpty() && Character.isLetter(text.charAt(0))) {
                description = text;
            }
        }

        if (max <= 0) return null;

        String name = stack.getName().getString();
        int colon = name.indexOf(':');
        String title = (colon > 0 ? name.substring(0, colon) : name).trim();
        if (description == null) description = title;

        String verb = "";
        String target = description;
        String dimension = null;
        String display = description;

        Matcher parts = DESCRIPTION_PATTERN.matcher(description);
        if (parts.find()) {
            verb = parts.group(1).toLowerCase(Locale.ROOT);
            String rest = parts.group(3).trim();
            display = Character.toUpperCase(verb.charAt(0)) + verb.substring(1) + " " + rest;

            Matcher dim = DIMENSION_PATTERN.matcher(rest);
            if (dim.find()) {
                target = dim.group(1).trim();
                dimension = dim.group(2).toLowerCase(Locale.ROOT);
            } else {
                target = rest;
            }
        }

        return new DailyQuest(title, display, verb, target, dimension, Math.min(current, max), max, expiresAt);
    }

    private static int parseNumber(String text) {
        try {
            return Integer.parseInt(text.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static String singular(String normalized) {
        if (normalized.endsWith("men")) return normalized.substring(0, normalized.length() - 3) + "man";
        if (normalized.endsWith("ies")) return normalized.substring(0, normalized.length() - 3) + "y";
        if (normalized.endsWith("s") && !normalized.endsWith("ss") && !normalized.endsWith("us")) {
            return normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String targetKey(DailyQuest quest) {
        return singular(normalize(quest.target));
    }

    private static boolean dimensionMatches(DailyQuest quest, String dimensionPath) {
        return quest.dimension == null || dimensionPath.contains(quest.dimension);
    }

    private static void progress(Predicate<DailyQuest> filter, int amount) {
        boolean changed = false;
        for (DailyQuest quest : quests.values()) {
            if (quest.current < quest.max && filter.test(quest)) {
                quest.current = Math.min(quest.max, quest.current + amount);
                changed = true;
            }
        }
        if (changed) saveQuests();
    }

    private static void onAttackBlock(BlockPos pos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!tracking(client) || quests.isEmpty()) return;

        String path = blockPath(client, pos);
        if (path.endsWith("air")) return;
        pendingBlocks.putIfAbsent(pos.toImmutable(), new PendingBlock(path, System.currentTimeMillis()));
    }

    private static String blockPath(MinecraftClient client, BlockPos pos) {
        return Registries.BLOCK.getId(client.world.getBlockState(pos).getBlock()).getPath();
    }

    private static void trackBlocks(MinecraftClient client, long now) {
        Iterator<Map.Entry<BlockPos, PendingBlock>> it = pendingBlocks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, PendingBlock> entry = it.next();
            PendingBlock pending = entry.getValue();

            if (!blockPath(client, entry.getKey()).equals(pending.path())) {
                it.remove();
                onBlockBroken(pending.path(), dimensionPath(client));
            } else if (now - pending.time() > PENDING_BLOCK_TIMEOUT_MS) {
                it.remove();
            }
        }
    }

    private static void onBlockBroken(String path, String dimensionPath) {
        progress(q -> BLOCK_VERBS.contains(q.verb)
                && dimensionMatches(q, dimensionPath)
                && blockMatches(targetKey(q), path), 1);
    }

    private static boolean blockMatches(String target, String path) {
        if (target.equals("block")) return true;
        if (target.equals("log") || target.equals("wood")) return path.endsWith("_log") || path.endsWith("_wood");
        if (target.equals("ore")) return path.endsWith("_ore");
        return normalize(path).equals(target);
    }

    private static void onAttackEntity(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!tracking(client) || quests.isEmpty()) return;

        if (!(entity instanceof LivingEntity) || entity.getType() == EntityType.PLAYER || entity.getType() == EntityType.ARMOR_STAND) return;

        AttackedMob mob = attackedMobs.computeIfAbsent(entity.getId(), id -> new AttackedMob());
        mob.lastHit = System.currentTimeMillis();
        mob.lastPos = entity.getBlockPos();
        mob.type = Registries.ENTITY_TYPE.getId(entity.getType()).getPath();
    }

    private static void trackKills(MinecraftClient client, long now) {
        Iterator<Map.Entry<Integer, AttackedMob>> it = attackedMobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, AttackedMob> entry = it.next();
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

            if (kill) onKill(mob.type, dimensionPath(client));
            if (done) it.remove();
        }
    }

    private static Map<?, ?> bossBars(MinecraftClient client) {
        try {
            if (!bossBarsLookedUp) {
                bossBarsLookedUp = true;
                for (Field field : BossBarHud.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(field.getType()) && !Modifier.isStatic(field.getModifiers())) {
                        field.setAccessible(true);
                        bossBarsField = field;
                        break;
                    }
                }
            }
            if (bossBarsField == null) return null;
            return (Map<?, ?>) bossBarsField.get(client.inGameHud.getBossBarHud());
        } catch (Exception e) {
            return null;
        }
    }

    private static void trackBossBars(MinecraftClient client) {
        if (quests.isEmpty()) return;

        Map<?, ?> bars = bossBars(client);
        if (bars == null) return;

        boolean changed = false;
        for (Object value : new ArrayList<>(bars.values())) {
            if (!(value instanceof ClientBossBar bar)) continue;

            String text = bar.getName().getString().trim();
            Matcher matcher = BOSS_BAR_PATTERN.matcher(text);
            if (!matcher.find()) continue;

            String title = matcher.group(1).trim();
            String key = title.toLowerCase(Locale.ROOT);
            if (text.equals(lastBossBars.get(key))) continue;
            lastBossBars.put(key, text);

            int shown = parseNumber(matcher.group(2));
            for (DailyQuest quest : quests.values()) {
                if (quest.title.equalsIgnoreCase(title) && quest.current != Math.min(quest.max, shown)) {
                    quest.current = Math.min(quest.max, shown);
                    changed = true;
                }
            }
        }
        if (changed) saveQuests();
    }

    private static void onKill(String typePath, String dimensionPath) {
        progress(q -> KILL_VERBS.contains(q.verb)
                && dimensionMatches(q, dimensionPath)
                && (ANY_MOB.contains(targetKey(q)) || normalize(typePath).equals(targetKey(q))), 1);
    }

    private static void trackOutputs(MinecraftClient client) {
        ScreenHandler handler = client.currentScreen instanceof HandledScreen<?> screen ? screen.getScreenHandler() : null;

        if (handler instanceof AbstractFurnaceScreenHandler) {
            ItemStack output = handler.slots.get(2).getStack();
            if (!lastFurnaceOutput.isEmpty()) {
                int taken = 0;
                if (output.isEmpty()) {
                    taken = lastFurnaceOutput.getCount();
                } else if (ItemStack.areItemsEqual(output, lastFurnaceOutput)) {
                    taken = lastFurnaceOutput.getCount() - output.getCount();
                }
                if (taken > 0) onFurnaceTaken(lastFurnaceOutput, taken, dimensionPath(client));
            }
            lastFurnaceOutput = output.copy();
        } else {
            lastFurnaceOutput = ItemStack.EMPTY;
        }

        if (handler instanceof BrewingStandScreenHandler) {
            for (int i = 0; i < lastPotions.length; i++) {
                ItemStack current = handler.slots.get(i).getStack();
                if (!lastPotions[i].isEmpty() && current.isEmpty() && isBrewedPotion(lastPotions[i])) {
                    String dimension = dimensionPath(client);
                    progress(q -> q.verb.equals("brew") && dimensionMatches(q, dimension), 1);
                }
                lastPotions[i] = current.copy();
            }
        } else {
            for (int i = 0; i < lastPotions.length; i++) lastPotions[i] = ItemStack.EMPTY;
        }
    }

    private static void onFurnaceTaken(ItemStack output, int amount, String dimensionPath) {
        String outputPath = normalize(Registries.ITEM.getId(output.getItem()).getPath());
        progress(q -> FURNACE_VERBS.contains(q.verb)
                && dimensionMatches(q, dimensionPath)
                && outputPath.contains(targetKey(q)), amount);
    }

    private static boolean isBrewedPotion(ItemStack stack) {
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return false;
        String path = contents.potion().get().getKey().map(key -> key.getValue().getPath()).orElse("");
        return !path.isEmpty() && !PLAIN_POTIONS.contains(path);
    }

    private static void onMessage(Text message) {
        if (!ModConfig.INSTANCE.enableDailyQuestTracker) return;

        String lower = message.getString().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();

        if (winTime > 0 && now - winTime > PAIR_WINDOW_MS) {
            winTime = 0;
            winDifficulty = null;
        }
        if (keyTime > 0 && now - keyTime > PAIR_WINDOW_MS) {
            keyTime = 0;
            keyDifficulty = null;
        }

        if (lower.contains("dungeons") && lower.contains("you received reward: win")) {
            winTime = now;
            winDifficulty = findDifficulty(hoverText(message));
        } else if (lower.contains("crates") && lower.contains("dungeon crate key")) {
            Matcher key = KEY_PATTERN.matcher(lower);
            keyDifficulty = key.find() ? findDifficulty(key.group(1)) : null;
            keyTime = now;
        } else {
            return;
        }

        if (winTime > 0 && keyTime > 0) {
            String difficulty = winDifficulty != null ? winDifficulty : keyDifficulty;
            winTime = 0;
            keyTime = 0;
            winDifficulty = null;
            keyDifficulty = null;

            int points = pointsFor(difficulty);
            if (points > 0) {
                progress(q -> normalize(q.target).contains("dungeonpoint"), points);
            }
        }
    }

    private static String hoverText(Text message) {
        StringBuilder hover = new StringBuilder();
        message.<Object>visit((style, str) -> {
            Object event = style.getHoverEvent();
            if (event != null) hover.append(event).append(' ');
            return Optional.empty();
        }, Style.EMPTY);
        return hover.toString();
    }

    private static String findDifficulty(String text) {
        Matcher matcher = DIFFICULTY_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        String word = matcher.group(1).toLowerCase(Locale.ROOT);
        return word.startsWith("nightm") ? "nightmare" : word;
    }

    private static int pointsFor(String difficulty) {
        if (difficulty == null) return 0;
        return switch (difficulty) {
            case "easy" -> 1;
            case "medium" -> 3;
            case "hard" -> 5;
            case "nightmare" -> 10;
            default -> 0;
        };
    }

    private static ModConfig.QuestStyle style() {
        ModConfig.QuestStyle style = ModConfig.INSTANCE.questStyle;
        return style == null ? ModConfig.QuestStyle.COMPACT : style;
    }

    private static boolean withBars() {
        ModConfig.QuestStyle style = style();
        return style == ModConfig.QuestStyle.BARS || style == ModConfig.QuestStyle.DETAILED_BARS;
    }

    private static List<DailyQuest> activeQuests() {
        long now = System.currentTimeMillis();
        List<DailyQuest> active = new ArrayList<>();
        for (DailyQuest quest : quests.values()) {
            if (quest.expiresAt > now) active.add(quest);
        }
        return active;
    }

    public static int getHeight() {
        int count = activeQuests().size();
        if (count == 0) return LINE_H * 2;
        return LINE_H + count * (withBars() ? BAR_ROW_H : LINE_H);
    }

    public static void render(DrawContext context, MinecraftClient client, int startX, int startY) {
        List<DailyQuest> active = activeQuests();
        boolean bars = withBars();

        long remaining = Math.max(0L, nextReset() - System.currentTimeMillis());
        MutableText header = Text.empty()
                .append(Text.literal("Resets in: ").formatted(Formatting.YELLOW))
                .append(Text.literal(formatDuration(remaining)).formatted(Formatting.RED, Formatting.UNDERLINE));
        drawOutlined(context, client.textRenderer, header, startX, startY);

        int y = startY + LINE_H;

        if (active.isEmpty()) {
            Text empty = Text.empty()
                    .append(Text.literal("Daily Quests: ").formatted(Formatting.YELLOW))
                    .append(Text.literal("Open /quests to track them").formatted(Formatting.DARK_GRAY));
            drawOutlined(context, client.textRenderer, empty, startX, y);
            return;
        }

        for (DailyQuest quest : active) {
            boolean done = quest.current >= quest.max;

            MutableText line = Text.empty();
            if (done) line.append(Text.literal("✔ ").formatted(Formatting.GREEN));
            line.append(Text.literal(titleCase(quest.display) + ": ").formatted(Formatting.YELLOW));
            line.append(Text.literal(format(quest.current) + "/" + format(quest.max))
                    .formatted(done ? Formatting.GREEN : Formatting.RED));

            drawOutlined(context, client.textRenderer, line, startX, y);
            if (bars) drawBar(context, startX, y + 10, BAR_WIDTH, quest.current, quest.max);

            y += bars ? BAR_ROW_H : LINE_H;
        }
    }

    private static String titleCase(String text) {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            boolean small = out.length() > 0 && SMALL_WORDS.contains(word.toLowerCase(Locale.ROOT));
            out.append(small ? word.toLowerCase(Locale.ROOT)
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.toString();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) return String.format(Locale.ROOT, "%dh %02dm %02ds", hours, minutes, seconds);
        if (minutes > 0) return String.format(Locale.ROOT, "%dm %02ds", minutes, seconds);
        return String.format(Locale.ROOT, "%ds", seconds);
    }

    private static String format(int value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static void drawOutlined(DrawContext context, TextRenderer renderer, Text text, int x, int y) {
        Text outlineText = Text.literal(text.getString()).formatted(Formatting.BLACK);

        context.drawText(renderer, outlineText, x - 1, y - 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x, y - 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y - 1, 0xFFFFFFFF, false);

        context.drawText(renderer, outlineText, x - 1, y, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y, 0xFFFFFFFF, false);

        context.drawText(renderer, outlineText, x - 1, y + 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x, y + 1, 0xFFFFFFFF, false);
        context.drawText(renderer, outlineText, x + 1, y + 1, 0xFFFFFFFF, false);

        context.drawTextWithShadow(renderer, text, x, y, 0xFFFFFFFF);
    }

    private static void drawBar(DrawContext context, int x, int y, int width, int current, int max) {
        context.fill(x - 1, y - 1, x + width + 1, y + 3, 0xFF000000);
        context.fill(x, y, x + width, y + 2, 0xFF3A3A3A);

        int filled = Math.min(width, Math.round(width * (current / (float) max)));
        int color = current >= max ? 0xFF55FF55 : 0xFFFF5555;
        if (filled > 0) {
            context.fill(x, y, x + filled, y + 2, color);
        }
    }
}