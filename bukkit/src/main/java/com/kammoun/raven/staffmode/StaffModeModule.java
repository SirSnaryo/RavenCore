package com.kammoun.raven.staffmode;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.command.CommandMapUtil;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.staffmode.command.EndSeeCommand;
import com.kammoun.raven.staffmode.command.InvSeeCommand;
import com.kammoun.raven.staffmode.command.StaffModeCommand;
import com.kammoun.raven.staffmode.command.VanishCommand;
import com.kammoun.raven.staffmode.invsee.InvSeeService;
import com.kammoun.raven.staffmode.listener.StaffModeListener;
import lombok.Getter;

@Getter
public class StaffModeModule extends KModule<RavenCore> {

    private StaffModeService service;
    private InvSeeService invSeeService;

    public StaffModeModule() {
        super("staffmode");
    }

    @Override
    public void onModuleEnable() {
        StaffModeBars bars = new StaffModeBars();
        StaffStateStore stateStore = api.getRedisMessenger() == null || api.getRedisMessenger().getConnection() == null
                ? null
                : new StaffStateStore(api, api.getRedisMessenger().getConnection().getPool());
        if (stateStore == null) {
            getLogger().warning("[StaffMode] Redis is unavailable, so staff mode and vanish will not"
                    + " carry over when players switch servers.");
        }

        service = new StaffModeService(api, getConfig(), bars, stateStore);
        invSeeService = new InvSeeService(api, getConfig());

        registerCommand(new StaffModeCommand(service), "staffmode");
        registerCommand(new VanishCommand(service), "vanish");
        // Another plugin may already own these labels; take them over so the toggles always run.
        CommandMapUtil.register(api, "staffmode");
        CommandMapUtil.register(api, "vanish");
        registerCommand(new InvSeeCommand(service, invSeeService), "invsee");

        EndSeeCommand endSee = new EndSeeCommand("endsee", service, invSeeService);
        EndSeeCommand eChest = new EndSeeCommand("echest", service, invSeeService);
        registerCommand(endSee, "endsee");
        registerCommand(eChest, "echest");
        // Another plugin may already own these labels; take them over so the staff tools always run.
        CommandMapUtil.register(api, "endsee");
        CommandMapUtil.register(api, "echest");

        registerEvent(new StaffModeListener(service, invSeeService));
        getLogger().info("[StaffMode] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (service != null) service.clear();
        if (invSeeService != null) invSeeService.clear();
        getLogger().info("[StaffMode] Disabled.");
    }
}
