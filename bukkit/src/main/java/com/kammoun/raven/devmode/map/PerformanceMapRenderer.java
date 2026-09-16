package com.kammoun.raven.devmode.map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.ArrayDeque;
import java.util.Deque;

public class PerformanceMapRenderer extends MapRenderer {

    private static final int SIZE = 128;
    private static final int HISTORY_SIZE = 64;

    private final JavaPlugin plugin;
    private final Type type;
    private final Deque<Double> history = new ArrayDeque<>();
    private final MapRenderThrottle throttle;
    private BufferedImage frame;

    public PerformanceMapRenderer(JavaPlugin plugin, Type type, long updateMs) {
        super(true);
        this.plugin = plugin;
        this.type = type;
        this.throttle = new MapRenderThrottle(updateMs);
    }

    @Override
    public void render(@NotNull MapView map, @NotNull MapCanvas canvas, @NotNull Player player) {
        long now = System.currentTimeMillis();
        if (throttle.shouldRender(now) || frame == null) {
            frame = buildFrame(player);
            canvas.drawImage(0, 0, frame);
        }
    }

    private BufferedImage buildFrame(Player player) {
        double value = sample(player);
        if (history.size() >= HISTORY_SIZE) {
            history.removeFirst();
        }
        history.addLast(value);

        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(new Color(8, 8, 10));
            g.fillRect(0, 0, SIZE, SIZE);
            g.setColor(type.accent);
            g.drawString(type.title, 8, 14);
            g.setColor(Color.WHITE);
            drawText(g, player);
            drawChart(g, type.scale);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void drawText(Graphics2D g, Player player) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        long used = heap.getUsed() / 1024L / 1024L;
        long max = heap.getMax() / 1024L / 1024L;
        switch (type) {
            case PERFORMANCE -> {
                g.drawString(String.format("TPS %.2f", Bukkit.getTPS()[0]), 8, 30);
                g.drawString(String.format("MSPT %.1f", Bukkit.getAverageTickTime()), 8, 44);
                g.drawString(plugin.getServer().getOnlinePlayers().size() + " online", 8, 58);
            }
            case MEMORY -> {
                g.drawString("Heap " + used + "/" + max + "M", 8, 30);
                g.drawString("Free " + ((max - used) / 1024L) + "G", 8, 44);
                g.drawString("GC " + ManagementFactory.getGarbageCollectorMXBeans().size() + " beans", 8, 58);
            }
            case NETWORK -> {
                g.drawString("Ping " + player.getPing() + "ms", 8, 30);
                g.drawString(plugin.getServer().getOnlinePlayers().size() + " online", 8, 44);
                g.drawString(player.getWorld().getName(), 8, 58);
            }
        }
    }

    private double sample(Player player) {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return switch (type) {
            case PERFORMANCE -> Bukkit.getAverageTickTime();
            case MEMORY -> heap.getUsed() / 1024D / 1024D;
            case NETWORK -> player.getPing();
        };
    }

    private void drawChart(Graphics2D g, double scale) {
        int left = 8;
        int bottom = 118;
        int height = 44;
        int width = 112;
        g.setColor(new Color(122, 122, 122));
        g.drawRect(left, bottom - height, width, height);
        if (history.isEmpty()) return;

        int i = 0;
        int previousX = left;
        int previousY = bottom;
        g.setColor(type.accent);
        for (double value : history) {
            int x = left + (int) ((i / (double) (HISTORY_SIZE - 1)) * width);
            int y = bottom - Math.min(height, (int) ((value / scale) * height));
            if (i > 0) {
                g.drawLine(previousX, previousY, x, y);
            }
            previousX = x;
            previousY = y;
            i++;
        }
    }

    public enum Type {
        PERFORMANCE("TPS/MSPT", new Color(168, 85, 247), 50D),
        MEMORY("MEMORY", new Color(51, 255, 102), 30_000D),
        NETWORK("NETWORK", new Color(76, 201, 240), 250D);

        private final String title;
        private final Color accent;
        private final double scale;

        Type(String title, Color accent, double scale) {
            this.title = title;
            this.accent = accent;
            this.scale = scale;
        }
    }
}
