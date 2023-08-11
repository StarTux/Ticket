package com.winthier.ticket;

import com.cavetale.core.bungee.Bungee;
import com.cavetale.core.connect.Connect;
import com.cavetale.core.perm.Perm;
import com.cavetale.core.playercache.PlayerCache;
import com.winthier.sql.SQLDatabase;
import com.winthier.ticket.event.TicketEvent;
import com.winthier.ticket.sql.SQLWebhook;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class TicketPlugin extends JavaPlugin implements Listener {
    private ConfigurationSection usageMessages;
    private SQLDatabase db;
    private static TicketPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        db = new SQLDatabase(this);
        db.registerTables(List.of(Ticket.class,
                                  Comment.class,
                                  SQLWebhook.class));
        db.createAllTables();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ticket").setUsage(Util.format(getCommand("ticket").getUsage()));
        Bukkit.getScheduler().runTaskTimer(this, this::reminder, 0L, 20L * 60L * 5L);
    }

    @Override
    public void onDisable() {
    }

    private String getUsageMessage(String key) {
        if (usageMessages == null) {
            usageMessages = YamlConfiguration.loadConfiguration(new InputStreamReader(getResource("usage.yml")));
        }
        return usageMessages.getString(key);
    }

    private void sendUsageMessage(CommandSender sender, String key) {
        if (sender.hasPermission("ticket." + key)) Util.sendMessage(sender, "&3Usage: " + getUsageMessage(key));
    }

    private void sendUsageMessage(CommandSender sender) {
        Util.sendMessage(sender, "&3Ticket usage:");
        for (String key : Arrays.asList("new", "view", "comment", "close", "reopen", "port", "assign", "reload", "reminder", "delete")) {
            if (sender.hasPermission("ticket." + key)) Util.sendMessage(sender, " " + getUsageMessage(key));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        try {
            if (args.length == 0) {
                if (sender.hasPermission("ticket.moderation")) {
                    listOpenTickets(sender);
                }
                if (sender instanceof Player) {
                    listOwnedTickets((Player) sender);
                }
            } else if ("new".equals(args[0])) {
                newTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("view".equals(args[0])) {
                viewTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("comment".equals(args[0])) {
                commentTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("close".equals(args[0])) {
                closeTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("reopen".equals(args[0])) {
                reopenTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("port".equals(args[0])) {
                portTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("assign".equals(args[0])) {
                assignTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("reload".equals(args[0])) {
                reload(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("reminder".equals(args[0])) {
                reminder(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("delete".equals(args[0])) {
                deleteTicket(sender, Arrays.<String>copyOfRange(args, 1, args.length));
            } else if ("migrate".equals(args[0]) && sender.isOp()) {
                int ticketsWithoutOwner = 0;
                int ticketsWithFoundOwner = 0;
                int ticketsWithoutAssignee = 0;
                int ticketsWithFoundAssignee = 0;
                for (Ticket ticket: db.find(Ticket.class).findList()) {
                    boolean shouldSave = false;
                    if (ticket.getOwnerUuid() == null) {
                        ticketsWithoutOwner += 1;
                        UUID uuid = PlayerCache.uuidForName(ticket.getOwnerName());
                        if (uuid != null) {
                            ticketsWithFoundOwner += 1;
                            ticket.setOwnerUuid(uuid);
                            shouldSave = true;
                        }
                    }
                    if (ticket.getAssigneeUuid() == null && ticket.isAssigned()) {
                        ticketsWithoutAssignee += 1;
                        UUID uuid = PlayerCache.uuidForName(ticket.getAssigneeName());
                        if (uuid != null) {
                            ticketsWithFoundAssignee += 1;
                            ticket.setAssigneeUuid(uuid);
                            shouldSave = true;
                        }
                    }
                    if (shouldSave) db.save(ticket);
                }
                int commentsWithoutCommenter = 0;
                int commentsWithFoundCommenter = 0;
                for (Comment comment: db.find(Comment.class).findList()) {
                    boolean shouldSave = false;
                    if (comment.getCommenterUuid() == null) {
                        commentsWithoutCommenter += 1;
                        UUID uuid = PlayerCache.uuidForName(comment.getCommenterName());
                        if (uuid != null) {
                            commentsWithFoundCommenter += 1;
                            comment.setCommenterUuid(uuid);
                            shouldSave = true;
                        }
                    }
                    if (shouldSave) db.save(comment);
                }
                sender.sendMessage(String.format("%d/%d Ticket owners found", ticketsWithFoundOwner, ticketsWithoutOwner));
                sender.sendMessage(String.format("%d/%d Ticket assignees found", ticketsWithFoundAssignee, ticketsWithoutAssignee));
                sender.sendMessage(String.format("%d/%d Commenters found", commentsWithFoundCommenter, commentsWithoutCommenter));
            } else {
                sendUsageMessage(sender);
            }
        } catch (UsageException ue) {
            sendUsageMessage(sender, ue.getKey());
        } catch (CommandException ce) {
            Util.sendMessage(sender, "&c%s", ce.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return Collections.emptyList();
    }

    private Ticket ticketById(int id) {
        List<Ticket> tickets = db.find(Ticket.class).where().eq("id", id).findList();
        if (tickets.isEmpty()) {
            throw new CommandException(String.format("Ticket [%d] not found.", id));
        }
        return tickets.get(0);
    }

    private Ticket ticketById(String arg) {
        // Fetch ticket.
        int id = -1;
        try {
            id = Integer.parseInt(arg);
        } catch (NumberFormatException nfe) { }
        if (id < 0) {
            throw new CommandException(String.format("Ticket ID expected, got: %s", arg));
        }
        return ticketById(id);
    }

    private void assertCommand(boolean condition, String message, Object... args) {
        if (!condition) throw new CommandException(String.format(message, args));
    }

    private void assertPermission(CommandSender sender, String permission) {
        assertCommand(sender.hasPermission(permission), "No permission.");
    }

    private void assertPlayer(CommandSender sender) {
        assertCommand(sender instanceof Player, "Player expected.");
    }

    private String compileMessage(String[] args, int beginIndex) {
        if (args.length <= beginIndex) return "";
        StringBuilder sb = new StringBuilder(args[beginIndex]);
        for (int i = beginIndex + 1; i < args.length; ++i) {
            sb.append(" ").append(args[i]);
        }
        return sb.toString();
    }

    private void listOwnedTickets(Player player) {
        db.find(Ticket.class).where().eq("ownerUuid", player.getUniqueId()).findListAsync(tickets -> listOwnedTicketsCallback(player, tickets));
    }

    private void listOwnedTicketsCallback(Player player, List<Ticket> tickets) {
        List<Ticket> opens = new ArrayList<Ticket>();
        for (Ticket ticket : tickets) {
            if (ticket.isOpen() || ticket.isUpdated()) opens.add(ticket);
        }
        if (opens.isEmpty()) {
            Util.tellRaw(player, Arrays.asList(new Object[] {
                        Util.format("&3Need staff assistance? Click here: "),
                        Util.commandSuggestButton("&3[&a\u270E &bNew Ticket&3]",
                                                  "&3Click here to make a new ticket.\n&3Leave a message in chat.", "/ticket new "),
                    }));
        } else {
            Util.sendMessage(player, "&3You have %d open ticket(s). Click below to view.", opens.size());
            for (Ticket ticket : opens) {
                ticket.sendShortInfo(player, false);
            }
        }
    }

    private void listOpenTickets(CommandSender sender) {
        db.find(Ticket.class).where().eq("open", true).findListAsync(tickets -> listOpenTicketsCallback(sender, tickets));
    }

    private void listOpenTicketsCallback(CommandSender sender, List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            sender.sendMessage(Component.text("No open ticket(s)", NamedTextColor.YELLOW));
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("" + tickets.size() + " open ticket(s)", NamedTextColor.YELLOW));
        for (Ticket ticket : tickets) {
            String cmd = "/ticket view " + ticket.getId();
            NamedTextColor color = !ticket.isSilent() && (!ticket.isAssigned() || ticket.isAssigneeUpdate())
                ? NamedTextColor.YELLOW
                : NamedTextColor.GRAY;
            NamedTextColor bgcolor = NamedTextColor.GRAY;
            TextComponent.Builder msg = Component.text()
                .clickEvent(ClickEvent.runCommand(cmd))
                .hoverEvent(Component.text(cmd, color))
                .color(color)
                .append(Component.text("[", bgcolor))
                .append(Component.text("\u21F2", NamedTextColor.GOLD))
                .append(Component.text("" + ticket.getId()))
                .append(Component.text("] ", bgcolor))
                .append(Component.text(ticket.getOwnerName()));
            if (ticket.isAssigned()) {
                msg.append(Component.text(" \u2192 ", bgcolor)); // rightwards arrow
                msg.append(Component.text(ticket.getAssigneeName()));
            }
            msg.append(Component.text(": ", bgcolor))
                .append(Component.text(ticket.getShortMessage()));
            lines.add(msg.build());
        }
        sender.sendMessage(Component.join(JoinConfiguration.separator(Component.newline()), lines));
    }

    private void viewTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.view");
        if (args.length != 1) throw new UsageException("view");
        Ticket ticket = ticketById(args[0]);
        if (!ticket.isOwner(sender)) assertPermission(sender, "ticket.view.any");
        if (!new TicketEvent(TicketEvent.Action.VIEW, ticket, sender).call()) {
            return;
        }
        List<Comment> comments = db.find(Comment.class).where().eq("ticketId", ticket.getId()).orderByAscending("id").findList();
        StringBuilder sb = new StringBuilder(ticket.getInfo());
        if (!comments.isEmpty()) {
            sb.append(Util.format("\n&3 Comments: &b%d", comments.size()));
            for (Comment comment : comments) {
                sb.append("\n ").append(comment.getInfo());
            }
        }
        sender.sendMessage(sb.toString());
        if (ticket.isOwner(sender) && ticket.isUpdated()) {
            ticket.setUpdated(false);
            db.updateAsync(ticket, null, "updated");
        } else if (!ticket.isAssigned() || ticket.isAssigned(sender)) {
            ticket.setAssigneeUpdate(false);
            db.updateAsync(ticket, null, "assignee_update");
        }
        // Display options
        ticket.sendOptions(sender);
    }

    private void newTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.new");
        assertPlayer(sender);
        Player player = (Player) sender;
        if (args.length == 0) throw new UsageException("new");
        assertCommand(args.length >= 3, "Write a message with at least 3 words.");
        int ticketCount = db.find(Ticket.class).where().eq("ownerUuid", player.getUniqueId()).eq("open", true).findRowCount();
        assertCommand(ticketCount < 3, "You already have %d open tickets.", ticketCount);
        Ticket ticket = new Ticket(getServerName(), player, compileMessage(args, 0));
        if (!new TicketEvent(TicketEvent.Action.CREATE, ticket, sender).call()) {
            return;
        }
        db.insertAsync(ticket, result -> newTicketCallback(sender, ticket, result));
    }

    private void newTicketCallback(CommandSender sender, Ticket ticket, int insertResult) {
        new TicketEvent(TicketEvent.Action.CREATED, ticket, sender).call();
        if (sender instanceof Player) {
            int id = ticket.getId();
            Util.tellRaw((Player) sender, Arrays
                         .asList(Util.format("&bTicket "),
                                 Util.commandRunButton("&3[&a\u21F2 &b" + id + "&3]", "&3Click to view the ticket", "/ticket view " + id),
                                 Util.format("&b created: &7%s", ticket.getMessage())
                                 ));
        } else {
            Util.sendMessage(sender, "&bTicket &3[&b%d&3]&b created: &7%s", ticket.getId(), ticket.getMessage());
        }
        if (!ticket.isSilent()) {
            notify(ticket.getId(), sender, "&e%s created ticket [%d]: %s", ticket.getOwnerName(), ticket.getId(), ticket.getMessage());
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket);
                    }
                });
        }
    }

    private void commentTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.comment");
        if (args.length < 2) throw new UsageException("comment");
        Ticket ticket = ticketById(args[0]);
        // assertCommand(args.length >= 4, "Write a comment with at least 3 words.");
        String message = compileMessage(args, 1);
        if (!ticket.isOwner(sender)) assertPermission(sender, "ticket.comment.any");
        Comment comment = new Comment(ticket.getId(), sender, message);
        if (!new TicketEvent(TicketEvent.Action.COMMENT, ticket, sender, comment).call()) {
            return;
        }
        db.insertAsync(comment, result -> commentTicketCallback(sender, ticket, comment, result));
    }

    /**
     * After the comment was saved, we update the ticket itself
     * accordingly.
     */
    private void commentTicketCallback(CommandSender sender, Ticket ticket, Comment comment, int insertResult) {
        Util.sendMessage(sender, "&bCommented on ticket &3[&b%d&3]&b: &7%s", ticket.getId(), comment.getComment());
        if (ticket.isOwner(sender)) {
            ticket.setAssigneeUpdate(true);
            db.updateAsync(ticket, null, "assignee_update");
        } else {
            ticket.setUpdated(true);
            db.updateAsync(ticket, null, "updated");
            Player owner = ticket.getOwner();
            if (owner != null) {
                Util.tellRaw(owner,
                             Util.commandRunButton("&3" + comment.getCommenterName() + " commented on your ticket [&b"
                                                   + ticket.getId() + "&3]: &7" + comment.getComment(),
                                                   "&3Click to view this ticket",
                                                   "/ticket view " + ticket.getId()));
            }
        }
        if (!ticket.isSilent()) {
            if (!ticket.isAssigned()) {
                notify(ticket.getId(), sender, "&e%s commented on ticket [%d]: %s", comment.getCommenterName(), comment.getTicketId(), comment.getComment());
            }
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket, "Comment", comment);
                    }
                });
        }
    }

    private void closeTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.close");
        if (args.length < 1) throw new UsageException("close");
        Ticket ticket = ticketById(args[0]);
        if (!ticket.isOwner(sender)) assertPermission(sender, "ticket.close.any");
        assertCommand(ticket.isOpen(), "Ticket [%d] is already closed.", ticket.getId());
        String message;
        String cMessage;
        if (args.length > 1) {
            cMessage = compileMessage(args, 1);
            message = "Closed: " + cMessage;
        } else {
            cMessage = "";
            message = "Closed";
        }
        Comment comment = new Comment(ticket.getId(), sender, message);
        if (!new TicketEvent(TicketEvent.Action.CLOSE, ticket, sender, comment).call()) {
            return;
        }
        db.insertAsync(comment, result -> closeTicketCallback(sender, ticket, comment, cMessage, result));
    }

    private void closeTicketCallback(CommandSender sender, Ticket ticket, Comment comment, String cMessage, int insertResult) {
        ticket.setOpen(false);
        if (ticket.isOwner(sender)) {
            ticket.setAssigneeUpdate(true);
            db.updateAsync(ticket, null, "open", "assignee_update");
        } else {
            ticket.setUpdated(true);
            db.updateAsync(ticket, null, "open", "updated");
            Player owner = ticket.getOwner();
            if (owner != null) {
                Util.tellRaw(owner,
                             Util.commandRunButton("&3" + comment.getCommenterName() + " closed your ticket [&b"
                                                   + ticket.getId() + "&3]: &7" + comment.getComment(),
                                                   "&3Click to view this ticket",
                                                   "/ticket view " + ticket.getId()));
            }
        }
        Util.sendMessage(sender, "&bTicket &3[&b%d&3]&b closed: &7%s", ticket.getId(), cMessage);
        if (!ticket.isSilent()) {
            notify(ticket.getId(), sender, "&e%s closed ticket [%d]: %s", comment.getCommenterName(), comment.getTicketId(), cMessage);
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket, "Closed", comment);
                    }
                });
        }
    }

    private void reopenTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.reopen");
        if (args.length < 1) throw new UsageException("reopen");
        Ticket ticket = ticketById(args[0]);
        if (!ticket.isOwner(sender)) assertPermission(sender, "ticket.reopen.any");
        assertCommand(!ticket.isOpen(), "Ticket [%d] is not closed.", ticket.getId());
        String message;
        String cMessage;
        if (args.length > 1) {
            cMessage = compileMessage(args, 1);
            message = "Reopened: " + cMessage;
        } else {
            cMessage = "";
            message = "Reopened";
        }
        Comment comment = new Comment(ticket.getId(), sender, message);
        if (!new TicketEvent(TicketEvent.Action.REOPEN, ticket, sender, comment).call()) {
            return;
        }
        db.insertAsync(comment, result -> reopenTicketCallback(sender, ticket, comment, cMessage, result));
    }

    private void reopenTicketCallback(CommandSender sender, Ticket ticket, Comment comment, String cMessage, int insertResult) {
        ticket.setOpen(true);
        if (ticket.isOwner(sender)) {
            ticket.setAssigneeUpdate(true);
            db.updateAsync(ticket, null, "open", "assignee_update");
        } else {
            ticket.setUpdated(true);
            Player owner = ticket.getOwner();
            db.updateAsync(ticket, null, "open", "updated");
            if (owner != null) {
                Util.tellRaw(owner,
                             Util.commandRunButton("&3" + comment.getCommenterName() + " reopened your ticket [&b"
                                                   + ticket.getId() + "&3]: &7" + comment.getComment(),
                                                   "&3Click to view this ticket",
                                                   "/ticket view " + ticket.getId()));
            }
        }
        Util.sendMessage(sender, "&bTicket &3[&b%d&3]&b reopened: &7%s", ticket.getId(), cMessage);
        if (!ticket.isSilent()) {
            notify(comment.getTicketId(), sender, "&e%s reopened ticket [%d]: %s",
                   comment.getCommenterName(), comment.getTicketId(), cMessage);
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket, "Reopen", comment);
                    }
                });
        }
    }

    private void portTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.port");
        assertPlayer(sender);
        Player player = (Player) sender;
        if (args.length != 1) throw new UsageException("port");
        Ticket ticket = ticketById(args[0]);
        // Try server.
        if (!new TicketEvent(TicketEvent.Action.PORT, ticket, sender).call()) {
            return;
        }
        if (!getServerName().equalsIgnoreCase(ticket.getServerName())) {
            Util.sendMessage(player, "&bTicket &3[&b%d&3]&b is on server %s...", ticket.getId(), ticket.getServerName());
            Bungee.send(player, ticket.getServerName());
            return;
        }
        // Try location.
        ticket.getLocation(location -> portTicketCallback(player, ticket, location));
    }

    private void portTicketCallback(final Player player, final Ticket ticket, final Location location) {
        if (location == null) {
            player.sendMessage(Component.text("Ticket location not found.", NamedTextColor.RED));
            return;
        }
        player.teleport(location);
        Util.sendMessage(player, "&bPorted to ticket &3[&b%d&3]&b.", ticket.getId());
        //Util.sendMessage(player, ticket.getInfo());
        // Assign
        if (ticket.isAssigned()) return;
        ticket.setAssignee(player);
        ticket.setUpdated(true);
        ticket.setAssigneeUpdate(false);
        if (!new TicketEvent(TicketEvent.Action.ASSIGN, ticket, player).call()) return;
        db.updateAsync(ticket, null, "assignee_name", "assignee_uuid", "updated", "assignee_update");
        Player owner = ticket.getOwner();
        if (owner != null) {
            Util.tellRaw(owner,
                         Util.commandRunButton("&3" + player.getName() + " was assigned to your ticket [&b" + ticket.getId() + "&3]",
                                               "&3Click to view this ticket",
                                               "/ticket view " + ticket.getId()));
        }
        if (!ticket.isSilent()) {
            notify(ticket.getId(), player, "&e%s was assigned to ticket [%d].", ticket.getAssigneeName(), ticket.getId());
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket, "Assigned", "to " + ticket.getAssigneeName());
                    }
                });
        }
    }

    private void assignTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.assign");
        if (args.length != 2) throw new UsageException("assign");
        Ticket ticket = ticketById(args[0]);
        PlayerCache playerCache = PlayerCache.forArg(args[1]);
        if (playerCache == null) {
            sender.sendMessage(Component.text("Player not found: " + args[1], NamedTextColor.RED));
            return;
        }
        if (!Perm.get().has(playerCache.uuid, "ticket.moderation")) {
            sender.sendMessage(Component.text("Player not a ticket moderator: " + playerCache.name, NamedTextColor.RED));
            return;
        }
        ticket.setAssignee(playerCache);
        ticket.setAssigneeUpdate(!ticket.isAssigned(sender));
        if (!new TicketEvent(TicketEvent.Action.ASSIGN, ticket, sender).call()) {
            return;
        }
        db.updateAsync(ticket, null, "assignee_uuid", "assignee_name", "assignee_update");
        Util.sendMessage(sender, "&bAssigned %s to ticket &3[&b%d&3]&b.", ticket.getAssigneeName(), ticket.getId());
        Player owner = ticket.getOwner();
        if (owner != null) {
            Util.tellRaw(owner,
                         Util.commandRunButton("&3" + ticket.getAssigneeName() + " was assigned to your ticket [&b" + ticket.getId() + "&3]",
                                               "&3Click to view this ticket",
                                               "/ticket view " + ticket.getId()));
        }
        if (!ticket.isSilent()) {
            notify(ticket.getId(), sender, "&e%s assigned %s to ticket [%d].", sender.getName(), ticket.getAssigneeName(), ticket.getId());
            db.scheduleAsyncTask(() -> {
                    for (SQLWebhook row : db.find(SQLWebhook.class).findList()) {
                        Webhook.send(this, row.getUrl(), ticket, "Assigned", "to " + ticket.getAssigneeName());
                    }
                });
        }
    }

    private void reload(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.reload");
        if (args.length > 0) throw new UsageException("reload");
        usageMessages = null;
        Util.sendMessage(sender, "&bTicket configuration reloaded.");
    }

    private void reminder(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.reminder");
        if (args.length > 0) throw new UsageException("reminder");
        Util.sendMessage(sender, "&bTriggering reminder...");
        reminder();
    }

    private void deleteTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.delete");
        if (args.length != 1) throw new UsageException("delete");
        Ticket ticket = ticketById(args[0]);
        new TicketEvent(TicketEvent.Action.DELETE, ticket, sender).call();
        int ticketCount = db.find(Ticket.class).eq("id", ticket.getId()).delete();
        int commentCount = db.find(Comment.class).eq("ticket_id", ticket.getId()).delete();
        sender.sendMessage(Component.text("Deleted ticket #" + ticket.getId() + ": " + ticketCount + " tickets, " + commentCount + " comments",
                                          NamedTextColor.YELLOW));
    }

    public void reminder() {
        db.find(Ticket.class)
            .eq("open", true)
            .findListAsync(this::reminderCallback);
    }

    private void reminderCallback(List<Ticket> tickets) {
        if (tickets.size() == 0) return;
        int unassigned = 0;
        Map<UUID, Ticket> adminUpdates = new HashMap<>();
        Map<UUID, Ticket> ownerUpdates = new HashMap<>();
        for (Ticket ticket : tickets) {
            if (ticket.isSilent()) continue;
            if (ticket.getAssigneeUuid() != null) {
                if (ticket.isAssigneeUpdate()) {
                    adminUpdates.put(ticket.getAssigneeUuid(), ticket);
                }
            } else {
                unassigned += 1;
            }
            if (ticket.isUpdated() && ticket.getOwnerUuid() != null) {
                ownerUpdates.put(ticket.getOwnerUuid(), ticket);
            }
        }
        if (unassigned > 0) {
            if (unassigned > 1) {
                notify("&eThere are %d unassigned tickets. Please attend to them.", unassigned);
            } else {
                notify("&eThere is an unassigned ticket. Please attend to it.");
            }
        }
        for (Map.Entry<UUID, Ticket> entry : adminUpdates.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Ticket ticket = entry.getValue();
            String cmd = "/ticket view " + ticket.getId();
            player.sendMessage(Component.text()
                               .content("Ticket [")
                               .color(NamedTextColor.YELLOW)
                               .append(Component.text("" + ticket.getId(), NamedTextColor.GOLD))
                               .append(Component.text("] by " + ticket.getOwnerName() + " has an update! Click for more info."))
                               .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.YELLOW)))
                               .clickEvent(ClickEvent.runCommand(cmd)));
        }
        for (Map.Entry<UUID, Ticket> entry : ownerUpdates.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Ticket ticket = entry.getValue();
            String cmd = "/ticket view " + ticket.getId();
            player.sendMessage(Component.text()
                               .content("Ticket [")
                               .color(NamedTextColor.DARK_AQUA)
                               .append(Component.text("" + ticket.getId(), NamedTextColor.AQUA))
                               .append(Component.text("] has an update for you! Click here for more info."))
                               .hoverEvent(HoverEvent.showText(Component.text(cmd, NamedTextColor.YELLOW)))
                               .clickEvent(ClickEvent.runCommand(cmd)));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("ticket.moderation")) {
            db.find(Ticket.class).where().eq("open", true).isNull("assignee_name").findRowCountAsync((tickets) -> {
                    if (tickets == 0) {
                        return;
                    } else if (tickets > 1) {
                        Util.sendMessage(player, "&eThere are %d open tickets. Please attend to them.", tickets);
                    } else {
                        Util.sendMessage(player, "&eThere is an open ticket. Please attend to it.");
                    }
                });
        }
        db.find(Ticket.class).where().eq("ownerUuid", player.getUniqueId()).eq("updated", true).findRowCountAsync((tickets) -> {
                if (tickets <= 0) return;
                Util.tellRaw(player, Arrays.asList(
                                                   Util.format("&3There are ticket updates for you. "),
                                                   Util.commandRunButton("&3[&bClick here&3]", "&3Click here for more info", "/ticket"),
                                                   Util.format("&3 for more info.")
                                                   ));
            });
    }

    public void notify(int id, CommandSender except, String message, Object... args) {
        message = Util.format(message, args);
        getLogger().info(ChatColor.stripColor(message));
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.equals(except) && player.hasPermission("ticket.notify")) {
                Util.tellRaw(player, Util.commandRunButton(message, "&3Click here for more info", "/ticket view " + id));
            }
        }
    }

    public void notify(String message, Object... args) {
        message = Util.format(message, args);
        getLogger().info(ChatColor.stripColor(message));
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.hasPermission("ticket.notify")) {
                Util.tellRaw(player, Util.commandRunButton(message, "&3Click here for more info", "/ticket"));
            }
        }
    }

    public String getServerName() {
        return Connect.get().getServerName();
    }

    public static void deleteTicket(int id) {
        instance.db.find(Ticket.class).eq("id", id).deleteAsync(r -> {
                instance.getLogger().info("Deleted ticket #" + id);
            });
        instance.db.find(Comment.class).eq("ticket_id", id).deleteAsync(r -> {
                instance.getLogger().info("Deleted " + r + " comments of ticket #" + id);
            });
    }
}
