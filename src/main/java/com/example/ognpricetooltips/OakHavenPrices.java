package com.example.ognpricetooltips;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.HashMap;
import java.util.Map;

public class OakHavenPrices {

    public static final String CURRENCY_SYMBOL = "☆";

    private record Entry(Item item, double price) {}

    private static final Map<String, Entry> PRICES = new HashMap<>();

    static {
        put("Azurite", Items.PAPER, 1.5);
        put("Jade", Items.PAPER, 4.5);
        put("Ruby", Items.PAPER, 12);
        put("Eclipsium", Items.PAPER, 75);
        put("Beautyberry", Items.BEETROOT, 3);
        put("Beautyberry Jam", Items.BREAD, 5);
        put("Beautyberry Pie", Items.PUMPKIN_PIE, 6);
        put("Snail Slime", Items.PAPER, 1);
        put("Snail Shell", Items.PAPER, 1);
        put("Blob of Trash", Items.PAPER, 2);
        put("Poisonous Vine", Items.PAPER, 4);
        put("Tuft of Leaves", Items.PAPER, 12);
        put("Silk", Items.PAPER, 3.5);
        put("Spider's Fang", Items.PAPER, 20);
        put("Twig", Items.PAPER, 3);
        put("Oak Plate", Items.PAPER, 18);
        put("Oak Spirit's Heart", Items.PAPER, 2500);
        put("Dry Leaf", Items.PAPER, 22);
    }

    private static void put(String name, Item item, double price) {
        PRICES.put(normalize(name), new Entry(item, price));
    }

    private static String normalize(String name) {
        return name.replace('\u2019', '\'');
    }

    public static double getSellPrice(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Entry entry = PRICES.get(normalize(stack.getName().getString()));
        if (entry == null || stack.getItem() != entry.item()) return 0;
        return entry.price();
    }

    public static String formatPrice(double price) {
        return String.format("%s%,.2f", CURRENCY_SYMBOL, price);
    }
}