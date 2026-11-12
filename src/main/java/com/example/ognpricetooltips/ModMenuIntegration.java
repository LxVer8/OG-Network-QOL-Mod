package com.example.ognpricetooltips;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ModMenuIntegration::getConfigScreen;
    }

    public static Screen getConfigScreen(Screen parent) {
        ModConfig config = ModConfig.INSTANCE;

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.literal("OG-Network QOL"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Text.literal("General"));

        general.addEntry(entryBuilder.startStrField(Text.literal("Menu Key Combo"), config.menuKey)
                .setDefaultValue("Y+U")
                .setSaveConsumer(newValue -> config.menuKey = newValue)
                .setTooltip(
                    Text.literal("The key(s) used to open this menu."),
                    Text.literal("Use + for combinations (e.g., Y+U)."),
                    Text.literal("Type NONE to disable the keybind entirely.")
                )
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Enable Price Tooltips"), config.enablePriceTooltips)
                .setDefaultValue(true)
                .setTooltip(
                    Text.literal("Shows the 'buy' and 'sell' prices at the bottom of the tooltip. This applies only to items available on /shop, and items that you can sell in /sell. Also shows the total value of the stack for that item.")
                )
                .setSaveConsumer(newValue -> config.enablePriceTooltips = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Enable Inventory Value"), config.enableInventoryValue)
                .setDefaultValue(true)
                .setTooltip(
                    Text.literal("Displays the total 'sell' value of all items in your inventory and open container. This applies only to items available on /shop, and items that you can sell in /sell. You can hover over the numbers to see a list of the most expensive items from that inventory.")
                )
                .setSaveConsumer(newValue -> config.enableInventoryValue = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Enable Shulker Box Value"), config.enableShulkerTooltip)
                .setDefaultValue(true)
                .setTooltip(
                    Text.literal("Adds a 'Total Value' number to the tooltip of Shulker Boxes, and it calculates the combined sell price of all items inside it. This applies only to items available on /shop, and items that you can sell in /sell.")
                )
                .setSaveConsumer(newValue -> config.enableShulkerTooltip = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Enable Rune Level Visualizer"), config.enableRuneLevelVisualizer)
                .setDefaultValue(true)
                .setTooltip(
                    Text.literal("Adds a dot to all Runes to indicate their level based on the possible max level of the Rune (green dot = max level // orange dot = a level bellow max // red dot = two or more levels bellow max).")
                )
                .setSaveConsumer(newValue -> config.enableRuneLevelVisualizer = newValue)
                .build());

        general.addEntry(entryBuilder.startBooleanToggle(Text.literal("Enable Rune Type Visualizer"), config.enableRuneTypeVisualizer)
                .setDefaultValue(true)
                .setTooltip(
                    Text.literal("Changes the texture of paper items used for Runes based on its kind (Omni, Astral, Celestial)")
                )
                .setSaveConsumer(newValue -> config.enableRuneTypeVisualizer = newValue)
                .build());

        builder.setSavingRunnable(() -> config.save());

        return builder.build();
    }
}