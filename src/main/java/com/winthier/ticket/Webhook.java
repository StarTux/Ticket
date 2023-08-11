package com.winthier.ticket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public final class Webhook {
    private Webhook() { }

    public static void send(final TicketPlugin plugin, final String url, final Ticket ticket) {
        Map<String, Object> webhookObject = new LinkedHashMap<>();
        int x = (int) Math.round(ticket.getX());
        int y = (int) Math.round(ticket.getY());
        int z = (int) Math.round(ticket.getZ());
        final String content = "New `" + ticket.getId() + "`"
            + " **" + ticket.getOwnerName() + "**: " + ticket.getMessage()
            + "\n**Server**: `" + ticket.getServerName() + "`"
            + "\n**Location**: `" + ticket.getWorldName() + " " + x + " " + y + " " + z + "`";
        webhookObject.put("content", content);
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        final String body = Util.GSON.toJson(webhookObject);
        HttpRequest request = HttpRequest.newBuilder()
            .POST(BodyPublishers.ofString(body))
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Webhook url=" + url + " ticket=" + ticket, e);
            e.printStackTrace();
        }
    }

    public static void send(final TicketPlugin plugin, final String url, final Ticket ticket, final String prefix, final Comment comment) {
        Map<String, Object> webhookObject = new LinkedHashMap<>();
        final String content = prefix + " `" + ticket.getId() + "`"
            + " **" + comment.getCommenterName() + "**: " + sanitize(comment.getComment());
        webhookObject.put("content", content);
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        final String body = Util.GSON.toJson(webhookObject);
        HttpRequest request = HttpRequest.newBuilder()
            .POST(BodyPublishers.ofString(body))
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Webhook url=" + url + " ticket=" + ticket, e);
            e.printStackTrace();
        }
    }

    public static void send(final TicketPlugin plugin, final String url, final Ticket ticket, final String prefix, final String suffix) {
        Map<String, Object> webhookObject = new LinkedHashMap<>();
        final String content = prefix + " `" + ticket.getId() + "` " + suffix;
        webhookObject.put("content", content);
        HttpClient client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        final String body = Util.GSON.toJson(webhookObject);
        HttpRequest request = HttpRequest.newBuilder()
            .POST(BodyPublishers.ofString(body))
            .uri(URI.create(url))
            .header("Content-Type", "application/json")
            .build();
        try {
            HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Webhook url=" + url + " ticket=" + ticket, e);
            e.printStackTrace();
        }
    }

    private static String sanitize(String in) {
        return in
            .replace("\\", "\\\\")
            .replace("`", "\\`")
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("/", "\\/")
            .replace("http://", "")
            .replace("https://", "");
    }
}
