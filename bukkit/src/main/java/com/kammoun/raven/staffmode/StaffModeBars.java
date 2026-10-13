package com.kammoun.raven.staffmode;

import com.kammoun.raven.library.platform.chat.ChatFormatter;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Staff mode and vanish each own their own bar so a player running both keeps both on screen.
 */
public final class StaffModeBars {

    private static final String STAFF_TEXT = "&fStaff Mode";
    private static final String VANISH_TEXT = "&fVanish";
    private static final String OWNER_VANISH_TEXT = "&fOwner Vanish";

    private final Map<UUID, BossBar> staffBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> vanishBars = new ConcurrentHashMap<>();

    public void showStaff(Player player) {
        BossBar bar = staffBars.computeIfAbsent(player.getUniqueId(), id -> create(STAFF_TEXT, BarColor.RED));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public void hideStaff(Player player) {
        BossBar bar = staffBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    public void showVanish(Player player, VanishTier tier) {
        boolean owner = tier == VanishTier.OWNER_VANISH;
        BossBar bar = vanishBars.computeIfAbsent(player.getUniqueId(),
                id -> create(owner ? OWNER_VANISH_TEXT : VANISH_TEXT, owner ? BarColor.PURPLE : BarColor.GREEN));
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
    }

    public void hideVanish(Player player) {
        BossBar bar = vanishBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    public void hideAll(Player player) {
        hideStaff(player);
        hideVanish(player);
    }

    public void clear() {
        staffBars.values().forEach(BossBar::removeAll);
        vanishBars.values().forEach(BossBar::removeAll);
        staffBars.clear();
        vanishBars.clear();
    }

    private BossBar create(String text, BarColor color) {
        return Bukkit.createBossBar(ChatFormatter.color(text), color, BarStyle.SOLID);
    }
}
