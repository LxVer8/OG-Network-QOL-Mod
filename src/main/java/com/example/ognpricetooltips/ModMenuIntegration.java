package com.example.ognpricetooltips;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.gui.ClothConfigScreen;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.text.Style;

public class ModMenuIntegration implements ModMenuApi {

    private static Text[] wrap(String tooltip) {
        return MinecraftClient.getInstance().textRenderer.getTextHandler()
                .wrapLines(tooltip, 220, Style.EMPTY).stream()
                .map(line -> (Text) Text.literal(line.getString()))
                .toArray(Text[]::new);
    }

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::getConfigScreen;
    }

    public static Screen getConfigScreen(Screen parent) {
        ModConfig config = ModConfig.INSTANCE;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("OG-Network QOL"));

        ConfigEntryBuilder eb = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(eb.startStrField(Text.literal("Open Menu Combo"), config.menuKey)
                .setDefaultValue("Y+U")
                .setSaveConsumer(newValue -> config.menuKey = newValue)
                .setTooltip(
                    wrap("The keys used to open this menu. Use + for combinations (for example, Y+U). Type NONE to disable the keybind entirely.")
                )
                .build());

        general.addEntry(eb.startKeyCodeField(
                        Text.literal("HUD Editor Key"),
                        InputUtil.fromTranslationKey(KeybindHandler.openHudEditorKey.getBoundKeyTranslationKey()))
                .setDefaultValue(KeybindHandler.openHudEditorKey.getDefaultKey())
                .setKeySaveConsumer(newKey -> {
                KeybindHandler.openHudEditorKey.setBoundKey(newKey);
                KeyBinding.updateKeysByCode();
                MinecraftClient.getInstance().options.write();
                })
                .setTooltip(wrap("The key used to open the HUD editor. Click the field, then press the key you want. Default: H."))
                .build());

        SubCategoryBuilder prices = eb.startSubCategory(Text.literal("Prices & Values"))
                .setExpanded(false);

        prices.add(toggle(eb, "Enable Price Tooltips", config.enablePriceTooltips, false,
                "Shows the 'buy' and 'sell' prices at the bottom of the tooltip. This applies only to items available on /shop, and items that you can sell in /sell. Also shows the total value of the stack for that item.",
                v -> config.enablePriceTooltips = v));

        prices.add(toggle(eb, "Enable Inventory Value", config.enableInventoryValue, false,
                "Displays the total 'sell' value of all items in your inventory and open container. This applies only to items available on /shop, and items that you can sell in /sell. You can hover over the numbers to see a list of the most expensive items from that inventory.",
                v -> config.enableInventoryValue = v));

        prices.add(toggle(eb, "Enable Shulker Box Value", config.enableShulkerTooltip, false,
                "Adds a 'Total Value' number to the tooltip of Shulker Boxes, and it calculates the combined sell price of all items inside it. This applies only to items available on /shop, and items that you can sell in /sell.",
                v -> config.enableShulkerTooltip = v));

        prices.add(toggle(eb, "Enable Ignore Renamed and Custom Items", config.enableItemNameCheck, false,
                "Skips items that have a different displayed name to their item ID. This applies to player-renamed items and server-renamed items.",
                v -> config.enableItemNameCheck = v));

        general.addEntry(prices.build());

        SubCategoryBuilder runes = eb.startSubCategory(Text.literal("Runes"))
                .setExpanded(false);

        runes.add(toggle(eb, "Enable Rune Level Visualizer", config.enableRuneLevelVisualizer, false,
                "Adds a dot to all Runes to indicate their level based on the possible max level of the Rune (green dot = max level // orange dot = a level bellow max // red dot = two or more levels bellow max).",
                v -> config.enableRuneLevelVisualizer = v));

        runes.add(toggle(eb, "Enable Rune Type Visualizer", config.enableRuneTypeVisualizer, false,
                "Changes the texture of paper items used for Runes based on its kind (Rare, Legendary, Celestial, Omni, Astral)",
                v -> config.enableRuneTypeVisualizer = v));

        runes.add(toggle(eb, "Enable Rune Custom Book Visualizer (not working)", config.enableCustomRuneBookTextures, false,
                "Changes the texture of paper items used for Runes based on the enchantment, applying the texture of a custom enchantment book texture pack that is installed.",
                v -> config.enableCustomRuneBookTextures = v));

        general.addEntry(runes.build());

        SubCategoryBuilder enchants = eb.startSubCategory(Text.literal("Enchantments"))
                .setExpanded(false);

        enchants.add(toggle(eb, "Enable Enchantment Color Fix", config.enableEnchantmentColorFix, false,
                "Colors the names of enchantments based on their Rune type.",
                v -> config.enableEnchantmentColorFix = v));

        enchants.add(toggle(eb, "Enable Enhancer Enchantment Indicator", config.enableOverMaxIndicator, false,
                "Adds a '+' at the beginning of an enchantment if its level exceeds the maximum possible level for that Rune. A '+' is added for evert extra level. (Sharpness 10 becomes ++Sharpness 10).",
                v -> config.enableOverMaxIndicator = v));

        enchants.add(toggle(eb, "Enable Overcapped Enchantment Fix", config.enableHighLevelEnchantFix, false,
                "Fixes the broken text on enchantments that go above the vanilla maximum level (e.g. 'Smite enchantment.level.11' becomes 'Smite XI').",
                v -> config.enableHighLevelEnchantFix = v));

        enchants.add(toggle(eb, "Enable Convert Roman Numerals", config.enableRomanToArabicFix,
                "Converts the roman numerals on enchantments into arabic numerals (e.g. 'Sharpness VIII' becomes 'Sharpness 8').",
                v -> config.enableRomanToArabicFix = v));

        general.addEntry(enchants.build());

        SubCategoryBuilder chat = eb.startSubCategory(Text.literal("Chat"))
                .setExpanded(false);

        chat.add(toggle(eb, "Enable Chat Message Color Remover", config.enableChatMessageColorRemover, false,
                "Recolors any message in chat to be plain white.",
                v -> config.enableChatMessageColorRemover = v));

        chat.add(toggle(eb, "Enable Chat Name Color Remover", config.enableChatNameColorRemover, false,
                "Recolors names to appear white.",
                v -> config.enableChatNameColorRemover = v));

        chat.add(toggle(eb, "Enable Chat Rank Remover", config.enableChatRankRemover, false,
                "Removes all ranks from chat.",
                v -> config.enableChatRankRemover = v));

        chat.add(toggle(eb, "Enable Chat Tag Remover", config.enableChatTagRemover, false,
                "Removes all tags from chat.",
                v -> config.enableChatTagRemover = v));

        chat.add(toggle(eb, "Enable Real Name Display", config.enableRealNameDisplay, false,
                "Removes the custom names that players can set in chat, displaying their actual name instead.",
                v -> config.enableRealNameDisplay = v));


        chat.add(toggle(eb, "Enable Server Announcement Remover", config.enableAnnouncementRemover, false,
                "Removes the automatic server messages entirely.",
                v -> config.enableAnnouncementRemover = v));
        general.addEntry(chat.build());

        builder.setSavingRunnable(config::save);

        builder.setDoesConfirmSave(false);
        builder.setAfterInitConsumer(screen -> {
            addSeparateSaveButton(screen);
            addEditHudButton(screen);
        });

        SubCategoryBuilder hudFolder = eb.startSubCategory(Text.literal("HUD"))
                .setExpanded(false);

        for (HudElements.Entry entry : HudElements.ALL) {
            hudFolder.add(toggle(eb, "Enable " + entry.name(), entry.get(),
                    entry.tooltip(),
                    entry::set));
        }

        general.addEntry(hudFolder.build());

        return builder.build();
    }

    private static void addEditHudButton(Screen screen) {
        ButtonWidget editHud = ButtonWidget.builder(Text.literal("Edit HUD Layout"), button ->
                        MinecraftClient.getInstance().setScreen(new HudEditScreen(screen)))
                .dimensions(screen.width - 110, 6, 104, 20)
                .build();

        Screens.getButtons(screen).add(editHud);
    }

    private static void addSeparateSaveButton(Screen screen) {
        List<ClickableWidget> widgets = Screens.getButtons(screen);

        int bottomY = Integer.MIN_VALUE;
        for (ClickableWidget w : widgets) {
            if (w instanceof ButtonWidget) bottomY = Math.max(bottomY, w.getY());
        }

        List<ButtonWidget> footer = new ArrayList<>();
        for (ClickableWidget w : widgets) {
            if (w instanceof ButtonWidget b && b.getY() == bottomY) footer.add(b);
        }
        if (footer.size() != 2) return;

        footer.sort(Comparator.comparingInt(ClickableWidget::getX));
        ButtonWidget cancel = footer.get(0);
        ButtonWidget saveAndQuit = footer.get(1);

        int left = cancel.getX();
        int span = saveAndQuit.getX() + saveAndQuit.getWidth() - left;
        int gap = 4;
        int width = (span - 2 * gap) / 3;

        cancel.setX(left);
        cancel.setWidth(width);
        saveAndQuit.setX(left + 2 * (width + gap));
        saveAndQuit.setWidth(width);

        ButtonWidget save = ButtonWidget.builder(Text.literal("Save"), button -> {
                    if (screen instanceof ClothConfigScreen clothScreen) {
                        clothScreen.saveAll(false); // save without closing
                        button.setMessage(Text.literal("Saved!"));
                    }
                })
                .dimensions(left + width + gap, bottomY, width, cancel.getHeight())
                .build();

        widgets.add(save);
    }

    private static BooleanListEntry toggle(ConfigEntryBuilder eb, String label, boolean current,
                                           String tooltip, Consumer<Boolean> onSave) {
        return toggle(eb, label, current, true, tooltip, onSave);
    }

    private static BooleanListEntry toggle(ConfigEntryBuilder eb, String label, boolean current,
                                           boolean defaultValue, String tooltip, Consumer<Boolean> onSave) {
        return eb.startBooleanToggle(Text.literal(label), current)
                .setDefaultValue(defaultValue)
                .setTooltip(wrap(tooltip))
                .setSaveConsumer(onSave)
                .build();
    }
}