package com.kammoun.raven.economy.gui;

import com.kammoun.raven.economy.hook.RavenEconomy;
import com.kammoun.raven.economy.log.EconomyLogRepository;
import com.kammoun.raven.economy.model.EconomyTransactionLog;
import com.kammoun.raven.library.item.ItemBuilder;
import com.kammoun.raven.library.platform.chat.ChatFormatter;
import com.kammoun.raven.library.tasks.SchedulerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class EconomyLogsMenu {

    private static final int SIZE = 54;
    private static final int PAGE_SIZE = 45;

    private final JavaPlugin plugin;
    private final EconomyLogRepository repository;
    private final RavenEconomy economy;
    private final Logger logger;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public EconomyLogsMenu(JavaPlugin plugin, EconomyLogRepository repository, RavenEconomy economy, Logger logger) {
        this.plugin = plugin;
        this.repository = repository;
        this.economy = economy;
        this.logger = logger;
    }

    public void open(Player player, boolean biggest) {
        player.sendMessage(ChatFormatter.color("&#7A7A7ALoading economy logs..."));
        SchedulerUtil.runAsync(() -> {
            try {
                List<EconomyTransactionLog> logs = repository.latest(PAGE_SIZE, biggest);
                SchedulerUtil.execute(player, () -> openLoaded(player, logs, biggest));
            } catch (Exception e) {
                logger.warning("[Economy] Failed to open logs GUI: " + e.getMessage());
                SchedulerUtil.execute(player, () -> player.sendMessage(ChatFormatter.color("&#D90429Failed to load economy logs.")));
            }
        });
    }

    public void open(Player player, UUID uuid) {
        player.sendMessage(ChatFormatter.color("&#7A7A7ALoading player economy logs..."));
        SchedulerUtil.runAsync(() -> {
            try {
                List<EconomyTransactionLog> logs = repository.byPlayer(uuid, PAGE_SIZE);
                SchedulerUtil.execute(player, () -> openLoaded(player, logs, false));
            } catch (Exception e) {
                logger.warning("[Economy] Failed to open player logs GUI: " + e.getMessage());
                SchedulerUtil.execute(player, () -> player.sendMessage(ChatFormatter.color("&#D90429Failed to load player economy logs.")));
            }
        });
    }

    private void openLoaded(Player player, List<EconomyTransactionLog> logs, boolean biggest) {
        EconomyLogsHolder holder = new EconomyLogsHolder(logs, biggest);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, ChatFormatter.menuTitle(biggest ? "Economy Biggest" : "Economy Logs"));
        holder.inventory(inventory);

        for (int i = 0; i < Math.min(PAGE_SIZE, logs.size()); i++) {
            inventory.setItem(i, item(logs.get(i)));
        }

        inventory.setItem(45, new ItemBuilder(Material.CLOCK)
                .name("&#4CC9F0Newest")
                .lore("&#7A7A7ALeft click to show newest transactions.")
                .build());
        inventory.setItem(53, new ItemBuilder(Material.GOLD_BLOCK)
                .name("&#FFD447Biggest")
                .lore("&#7A7A7ALeft click to show biggest transfers.")
                .build());
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof EconomyLogsHolder holder)) return;

        int slot = event.getRawSlot();
        if (slot == 45) {
            open(player, false);
            return;
        }
        if (slot == 53) {
            open(player, true);
            return;
        }
        if (slot < 0 || slot >= holder.logs().size()) {
            return;
        }

        EconomyTransactionLog log = holder.logs().get(slot);
        if (event.isRightClick() && log.targetUuid() != null) {
            open(player, log.targetUuid());
            return;
        }

        player.sendMessage(ChatFormatter.color("&#7A7A7A&m                                                "));
        player.sendMessage(ChatFormatter.color("&#FF2B2BTransaction &#FFFFFF" + log.transactionId()));
        player.sendMessage(ChatFormatter.color("&#7A7A7AType: &#FFFFFF" + log.type().name()));
        player.sendMessage(ChatFormatter.color("&#7A7A7AAmount: &#33FF66" + economy.format(log.amount())));
        player.sendMessage(ChatFormatter.color("&#7A7A7ABefore: &#FFFFFF" + economy.format(log.balanceBefore())
                + " &#7A7A7AAfter: &#FFFFFF" + economy.format(log.balanceAfter())));
        player.sendMessage(ChatFormatter.color("&#7A7A7AActor: &#FFFFFF" + safe(log.actorName())
                + " &#7A7A7ATarget: &#FFFFFF" + safe(log.targetName())));
        player.sendMessage(ChatFormatter.color("&#7A7A7AReason: &#FFFFFF" + safe(log.reason())));
        player.sendMessage(ChatFormatter.color("&#7A7A7ARaw: &#FFFFFF" + safe(log.rawCommand())));
        player.sendMessage(ChatFormatter.color("&#7A7A7AServer: &#FFFFFF" + safe(log.serverDisplayName())));
    }

    private ItemStack item(EconomyTransactionLog log) {
        ItemStack stack = new ItemStack(log.targetUuid() == null ? Material.PAPER : Material.PLAYER_HEAD);
        if (log.targetUuid() != null && stack.getItemMeta() instanceof SkullMeta skullMeta) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(log.targetUuid());
            skullMeta.setOwningPlayer(offlinePlayer);
            stack.setItemMeta(skullMeta);
        }

        List<String> lore = new ArrayList<>();
        lore.add("&#7A7A7Aᴀᴍᴏᴜɴᴛ &#33FF66" + economy.format(log.amount()));
        lore.add("&#7A7A7Aʙᴇғᴏʀᴇ &#FFFFFF" + economy.format(log.balanceBefore()));
        lore.add("&#7A7A7Aᴀғᴛᴇʀ &#FFFFFF" + economy.format(log.balanceAfter()));
        lore.add("&#7A7A7Aᴀᴄᴛᴏʀ &#FFFFFF" + safe(log.actorName()));
        lore.add("&#7A7A7Aᴛᴀʀɢᴇᴛ &#FFFFFF" + safe(log.targetName()));
        lore.add("&#7A7A7Aʀᴇᴀsᴏɴ &#FFFFFF" + safe(log.reason()));
        lore.add("&#7A7A7Aᴄᴏᴍᴍᴀɴᴅ &#FFFFFF" + safe(log.rawCommand()));
        lore.add("&#7A7A7Asᴇʀᴠᴇʀ &#FFFFFF" + safe(log.serverDisplayName()));
        lore.add("&#7A7A7Aᴛɪᴍᴇ &#FFFFFF" + dateFormat.format(new Date(log.createdAt())));
        lore.add("");
        lore.add("&#4CC9F0Left click &#7A7A7Afull details");
        lore.add("&#4CC9F0Right click &#7A7A7Afilter player");

        return new ItemBuilder(stack)
                .name((log.success() ? "&#33FF66" : "&#D90429") + log.type().name())
                .lore(lore)
                .build();
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}
