package com.kammoun.raven.rtpzone.papi;

import com.kammoun.raven.library.platform.placeholder.KPlaceholderExpansion;
import com.kammoun.raven.rtpzone.RtpZoneModule;
import org.bukkit.plugin.java.JavaPlugin;


public class RtpZonePlaceholders extends KPlaceholderExpansion {

    public RtpZonePlaceholders(JavaPlugin plugin, RtpZoneModule module) {
        super(plugin, "rtpzone");
        register("time", player -> String.valueOf(module.getCurrentTime()));
    }
}
