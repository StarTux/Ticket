package com.winthier.ticket;

import org.bukkit.scheduler.BukkitRunnable;

public final class ReminderTask {
    private final TicketPlugin plugin;
    private BukkitRunnable task;

    public ReminderTask(final TicketPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        long delay = 10L * 20L * 60L;
        if (delay < 0L) return;
        task = new BukkitRunnable() {
            public void run() {
                plugin.reminder();
            }
        };
        try {
            task.runTaskTimer(plugin, delay, delay);
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
    }

    public void stop() {
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
        task = null;
    }

    public void restart() {
        stop();
        start();
    }
}
