package com.kammoun.raven.spawner.config;

import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.util.PlaceholderHelper;
import lombok.Getter;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;

@Getter
public class SpawnerConfig {

    private final KModule<?> module;

    private int maxStack;
    private int activationRadius;
    private boolean placeParticles;
    private boolean explosionDestroys;
    private boolean silkTouchRequired;
    private boolean silkTouchPermission;
    private boolean ownershipEnabled;
    private boolean convertNatural;

    private final EnumMap<Msg, String> messages = new EnumMap<>(Msg.class);
    private final EnumMap<Sfx, Sound> sounds = new EnumMap<>(Sfx.class);

    public SpawnerConfig(KModule<?> module) {
        this.module = module;
        reload();
    }

    public void reload() {
        module.reloadConfig();
        FileConfiguration cfg = module.getConfig();

        maxStack            = cfg.getInt("max-stack", 100000);
        activationRadius    = cfg.getInt("activation-radius", 25);
        placeParticles      = cfg.getBoolean("place-particles", false);
        explosionDestroys   = cfg.getBoolean("explosion-destroys", false);
        silkTouchRequired   = cfg.getBoolean("silk-touch.required", true);
        silkTouchPermission = cfg.getBoolean("silk-touch.use-permission", true);
        ownershipEnabled    = cfg.getBoolean("ownership.enabled", false);
        convertNatural      = cfg.getBoolean("convert-natural", true);

        loadMessages(cfg);
        loadSounds(cfg);
    }

    private void loadMessages(FileConfiguration cfg) {
        messages.clear();
        for (Msg msg : Msg.values()) {
            messages.put(msg, cfg.getString("messages." + msg.key, msg.fallback));
        }
    }

    private void loadSounds(FileConfiguration cfg) {
        sounds.clear();
        for (Sfx sfx : Sfx.values()) {
            String name = cfg.getString("sounds." + sfx.key, sfx.fallbackName);
            try {
                sounds.put(sfx, Sound.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                module.getLogger().warning("Unknown sound '" + name + "' for sounds." + sfx.key);
            }
        }
    }

    public void sendMessage(Player player, Msg msg, String... placeholders) {
        String raw = messages.getOrDefault(msg, msg.fallback);
        if (raw == null || raw.isEmpty()) return;
        player.sendMessage(ChatFormatter.color(PlaceholderHelper.parsePlaceholders(raw, placeholders)));
    }

    public void sendActionBar(Player player, Msg msg, String... placeholders) {
        String raw = messages.getOrDefault(msg, msg.fallback);
        if (raw == null || raw.isEmpty()) return;
        player.sendActionBar(ChatFormatter.color(PlaceholderHelper.parsePlaceholders(raw, placeholders)));
    }

    public void playSound(Player player, Sfx sfx) {
        Sound sound = sounds.get(sfx);
        if (sound != null) player.playSound(player.getLocation(), sound, 1f, 1f);
    }

    public enum Msg {
        NO_SILK        ("no-silk",         "&cYou need Silk Touch to break this spawner!"),
        SELL_BAR       ("sell-bar",         "&#38f616+$%amount%"),
        SELL_CHAT      ("sell-chat",        ""),
        XP_BAR         ("xp-collected-bar", "&#80ee0b+%xp% XP collected"),
        STACKED_BAR    ("stacked-bar",      "&#ee340bStack is now x%total% (%type%)"),
        INVENTORY_FULL ("inventory-full",   "&cYour inventory is full!"),
        NOT_OWNER      ("not-owner",        "&cYou don't own this spawner!"),
        STORAGE_IN_USE ("storage-in-use",   "&cSomeone else is already accessing this spawner."),
        STACK_LIMIT    ("stack-limit",      "&cStack limit reached (&f%max%&c).");

        public final String key;
        public final String fallback;

        Msg(String key, String fallback) {
            this.key = key;
            this.fallback = fallback;
        }
    }

    public enum Sfx {
        NO_SILK        ("no-silk",       "ENTITY_VILLAGER_NO"),
        PAGE_SWITCH    ("page-switch",   "ITEM_BOOK_PAGE_TURN"),
        CLICK          ("click",         "UI_BUTTON_CLICK"),
        INVENTORY_FULL ("inventory-full","ENTITY_VILLAGER_NO"),
        LIMIT_REACHED  ("limit-reached", "ENTITY_VILLAGER_NO");

        public final String key;
        public final String fallbackName;

        Sfx(String key, String fallbackName) {
            this.key = key;
            this.fallbackName = fallbackName;
        }
    }
}
