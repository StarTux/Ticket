package com.winthier.ticket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class Util {
    private Util() { }
    protected static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static String format(String msg, Object... args) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        msg = String.format(msg, args);
        return msg;
    }

    public static String colorize(String msg) {
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public static void sendMessage(CommandSender sender, String msg, Object... args) {
        sender.sendMessage(format(msg, args));
    }

    public static String formatDate(long time) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(time));
        DateFormatSymbols symbols = DateFormatSymbols.getInstance();
        return String.format("%s %02d %d, %02d:%02d",
                             symbols.getShortMonths()[cal.get(Calendar.MONTH)],
                             cal.get(Calendar.DAY_OF_MONTH),
                             cal.get(Calendar.YEAR),
                             cal.get(Calendar.HOUR_OF_DAY),
                             cal.get(Calendar.MINUTE));
    }

    public static String formatInterval(long interval) {
        final int seconds = (int) (interval / 1000);
        final int minutes = seconds / 60;
        final int hours = minutes / 60;
        int days = hours / 24;
        // display hours, minutes and seconds if less than a day ago
        if (days == 0) {
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
        // calculate years and months
        final int years = days / 365;
        days -= years * 365;
        final int months = days / 30;
        days -= months * 30;
        // build the string
        StringBuilder sb = new StringBuilder();
        if (years > 0) sb.append(years).append("y");
        if (months > 0) sb.append(months).append("m");
        if (days > 0) sb.append(days).append("d");
        return sb.toString();
    }

    public static Object commandButton(String label, String tooltip, String command, boolean run) {
        label = colorize(label);
        tooltip = colorize(tooltip);
        Map<String, Object> result = new HashMap<>();
        result.put("text", label);
        Map<String, String> tooltipMap = new HashMap<>();
        Map<String, String> commandMap = new HashMap<>();
        result.put("hoverEvent", tooltipMap);
        result.put("clickEvent", commandMap);
        tooltipMap.put("action", "show_text");
        tooltipMap.put("value", tooltip);
        commandMap.put("action", run ? "run_command" : "suggest_command");
        commandMap.put("value", command);
        return result;
    }

    public static Object commandRunButton(String label, String tooltip, String command) {
        return commandButton(label, tooltip, command, true);
    }

    public static void tellRaw(Player player, Object raw) {
        String json = GSON.toJson(raw);
        Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "minecraft:tellraw " + player.getName() + " " + json);
    }
}
