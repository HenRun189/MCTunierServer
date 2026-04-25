package me.HenRun189.tunierServer.achievement;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class AchievementManager {

    private final Random random = new Random();


    public static final List<String> ADVANCEMENT_POOL = List.of(

            // BASIC
            "story/mine_stone", // Stone Age
            "story/smelt_iron",          // Acquire Hardware
            "story/upgrade_tools",       // Getting an Upgrade
            "story/iron_tools",          // Isn’t It Iron Pick
            "story/obtain_armor",        // Suit Up

            // NETHER EASY
            "story/lava_bucket",         // Hot Stuff
            "story/form_obsidian",       // Ice Bucket Challenge
            "story/enter_the_nether",    // We Need to Go Deeper
            //"nether/find_bastion",     // Those Were the Days
            //"nether/find_fortress",    // A Terrible Fortress

            // COMBAT
            "adventure/kill_a_mob",      // Monster Hunter
            //"adventure/kill_all_mobs", // Monsters Hunted
            "adventure/shoot_arrow",     // Take Aim

            // FOOD / FARM
            "husbandry/breed_an_animal", // The Parrots and the Bats
            "husbandry/tame_an_animal",  // Best Friends Forever
            "husbandry/fishy_business",  // Fishy Business
            "husbandry/tactical_fishing", // Tactical Fishing → Fisch mit Eimer einsammeln
            "husbandry/plant_seed", // Plant a Seed → Samen pflanzen (Weizen etc.)

            // MINING / LOOT
            "story/mine_diamond",        // Diamonds!
            //"story/enchant_item",        // Enchanter
            "adventure/trade",           // What a Deal!

            // MOVEMENT / EASY FUN
            "adventure/sleep_in_bed",    // Sweet Dreams
            //"adventure/root",            // Adventure
            //"adventure/swim_in_water",   // (Root-abhängiger Swim/Water-Advancement-Name prüfen)
            //"adventure/ride_a_boat",      // (Boat-Advancement-Name prüfen)


            //"adventure/read_power_of_chiseled_bookshelf", // Bücherregal benutzen

            "nether/deflect_arrow", // Not Today, Thank You → Pfeil mit Schild blocken
            "nether/distract_piglin", // Oh Shiny → Gold zu Piglin werfen
            "adventure/bullseye", // Bullseye → Zielscheibe mittig treffen
            "adventure/ol_betsy", // Ol' Betsy → Armbrust schießen
            "adventure/play_jukebox" // Sound of Music → Musik abspielen

    );

    public String getRandomAdvancement() {
        if (ADVANCEMENT_POOL.isEmpty()) {
            return "nether/find_fortress";
        }

        return ADVANCEMENT_POOL.get(random.nextInt(ADVANCEMENT_POOL.size()));
    }

    public String getDisplayName(String key) {

        Advancement adv = Bukkit.getAdvancement(NamespacedKey.minecraft(key));

        if (adv == null || adv.getDisplay() == null) {
            return formatFallback(key);
        }

        return PlainTextComponentSerializer.plainText()
                .serialize(adv.getDisplay().title());
    }

    public String getDescription(String key) {

        Advancement adv = Bukkit.getAdvancement(NamespacedKey.minecraft(key));

        if (adv == null || adv.getDisplay() == null) {
            return "No description available.";
        }

        return PlainTextComponentSerializer.plainText()
                .serialize(adv.getDisplay().description());
    }

    private String formatFallback(String key) {

        String formatted = key
                .replace("story/", "")
                .replace("_", " ")
                .toLowerCase(Locale.ROOT);

        if (formatted.isEmpty()) {
            return key;
        }

        return formatted.substring(0, 1).toUpperCase() + formatted.substring(1);
    }
}

//GPT 15:15