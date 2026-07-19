package com.kammoun.raven.billford.command;

import com.kammoun.raven.billford.BillfordModule;
import com.kammoun.raven.billford.config.BillfordConfig;
import com.kammoun.raven.billford.config.BillfordMessages;
import com.kammoun.raven.billford.manager.BillfordManager;
import com.kammoun.raven.billford.manager.GuiManager;
import com.kammoun.raven.billford.model.BillfordPreset;
import com.kammoun.raven.library.command.KCommand;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class BillfordPresetCommand extends KCommand {

    private final BillfordModule module;

    public BillfordPresetCommand(BillfordModule module) {
        super("billfordpreset", "raven.billford.admin", false);
        this.module = module;
    }

    private BillfordManager manager() { return module.getManager(); }
    private GuiManager gui()          { return module.getGuiManager(); }
    private BillfordMessages messages() { return module.getMessages(); }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(sender, args);
            case "edit"   -> handleEdit(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "switch" -> handleSwitch(sender, args);
            case "list"   -> handleList(sender);
            case "info"   -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            default       -> sendHelp(sender);
        }
    }

    private void handleCreate(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "preset.player-only");
            return;
        }
        if (args.length < 2) {
            messages().send(sender, "preset.usage-create");
            return;
        }
        String name = args[1];
        if (manager().presetExists(name)) {
            messages().send(sender, "preset.create-exists", "preset", name);
            return;
        }
        gui().openEditor(player, new BillfordPreset(name));
    }

    private void handleEdit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages().send(sender, "preset.player-only");
            return;
        }
        if (args.length < 2) {
            messages().send(sender, "preset.usage-edit");
            return;
        }
        BillfordPreset preset = manager().getPreset(args[1]);
        if (preset == null) {
            messages().send(sender, "preset.not-found", "preset", args[1]);
            return;
        }
        gui().openEditor(player, preset);
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "preset.usage-delete");
            return;
        }
        String name = args[1];
        if (!manager().presetExists(name)) {
            messages().send(sender, "preset.not-found", "preset", name);
            return;
        }
        if (name.equals(manager().getCurrentPresetName())) {
            messages().send(sender, "preset.cant-delete-active");
            return;
        }
        manager().deletePreset(name, ok ->
                messages().send(sender, ok ? "preset.deleted" : "preset.delete-failed", "preset", name));
    }

    private void handleSwitch(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages().send(sender, "preset.usage-switch");
            return;
        }
        String name = args[1];
        if (!manager().presetExists(name)) {
            messages().send(sender, "preset.not-found", "preset", name);
            return;
        }
        manager().switchPreset(name, ok ->
                messages().send(sender, ok ? "preset.switched" : "preset.switch-failed", "preset", name));
    }

    private void handleList(CommandSender sender) {
        var presets = manager().getPresets();
        if (presets.isEmpty()) {
            messages().send(sender, "preset.list-empty");
            return;
        }
        String current = manager().getCurrentPresetName();
        messages().send(sender, "preset.list-header");
        for (BillfordPreset preset : presets.values()) {
            String key = preset.getName().equals(current) ? "preset.list-entry-active" : "preset.list-entry";
            String state = preset.isEnabled() ? "enabled" : "disabled";
            messages().send(sender, key, "preset", preset.getName(), "state", state);
        }
    }

    private void handleInfo(CommandSender sender) {
        BillfordPreset current = manager().getCurrentPreset();
        if (current == null) {
            messages().send(sender, "no-preset");
            return;
        }
        messages().send(sender, "preset.info-header");
        messages().send(sender, "preset.info-name", "preset", current.getName());
        messages().send(sender, "preset.info-required", "count", current.getRequiredItemsCount());
        messages().send(sender, "preset.info-next", "time", BillfordConfig.formatDuration(manager().timeUntilNextChange()));
    }

    private void handleReload(CommandSender sender) {
        module.reload();
        messages().send(sender, "preset.reloaded");
    }

    private void sendHelp(CommandSender sender) {
        messages().getList("preset.help").forEach(sender::sendMessage);
    }

    @Override
    public List<String> onTab(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("create", "edit", "delete", "switch", "list", "info", "reload"), args[0]);
        }
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("edit") || sub.equals("delete") || sub.equals("switch")) {
                return filter(new ArrayList<>(manager().getPresets().keySet()), args[1]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
