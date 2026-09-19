package com.kammoun.raven.placeholder;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.library.module.KModule;
import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import lombok.Getter;

@Getter
public class PlaceholderModule extends KModule<RavenCore> {

    private RavenPlaceholders placeholders;
    private RavenPlaceholderExpansion expansion;

    public PlaceholderModule() {
        super("placeholder");
    }

    @Override
    public void onModuleEnable() {
        placeholders = new RavenPlaceholders(api);
        registerPlaceholders();
        getLogger().info("[Placeholder] Enabled.");
    }

    @Override
    public void onModuleDisable() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        placeholders = null;
        getLogger().info("[Placeholder] Disabled.");
    }

    @Override
    public void reload() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        placeholders = new RavenPlaceholders(api);
        registerPlaceholders();
    }

    private void registerPlaceholders() {
        if (!KPlaceholderExpansion.isAvailable()) {
            getLogger().info("[Placeholder] PlaceholderAPI not found; %raven_*% placeholders will resolve internally only.");
            return;
        }

        expansion = new RavenPlaceholderExpansion(api, placeholders);
        if (expansion.register()) {
            getLogger().info("[Placeholder] Registered %raven_*% PlaceholderAPI placeholders.");
        } else {
            getLogger().warning("[Placeholder] Could not register %raven_*% placeholders because the identifier is already taken.");
        }
    }
}
