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
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
import static com.cavetale.mytems.util.Text.wrapLore;
import static net.kyori.adventure.text.Component.join;
import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.textOfChildren;
import static net.kyori.adventure.text.JoinConfiguration.separator;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;
import static net.kyori.adventure.text.event.ClickEvent.suggestCommand;
import static net.kyori.adventure.text.event.HoverEvent.showText;
import static net.kyori.adventure.text.format.NamedTextColor.*;

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
        if (sender.hasPermission("ticket." + key)) sender.sendMessage(text("Usage: " + getUsageMessage(key), DARK_AQUA));
    }

    private void sendUsageMessage(CommandSender sender) {
        sender.sendMessage(text("Ticket usage:", DARK_AQUA));
        for (String key : Arrays.asList("new", "view", "comment", "close", "reopen", "port", "assign", "reload", "reminder", "delete")) {
            if (!sender.hasPermission("ticket." + key)) {
                continue;
            }
            sender.sendMessage(text(" " + getUsageMessage(key), DARK_AQUA));
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
            sender.sendMessage(text(ce.getMessage(), RED));
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
        final int len = sb.length();
        for (int i = 0; i < len; i += 1) {
            int c = (int) sb.charAt(i);
            if (c >= 0x0200 || c == 0x00A7) {
                sb.setCharAt(i, '?');
            }
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
            player.sendMessage(textOfChildren(text("Need staff assistance? Click here: ", DARK_AQUA),
                                              textOfChildren(text("[", DARK_AQUA),
                                                             text("\u270e", GREEN), text(" New Ticket", AQUA),
                                                             text("]", DARK_AQUA))
                                              .hoverEvent(showText(textOfChildren(text("Click here to make a new ticket.", DARK_AQUA),
                                                                                  newline(),
                                                                                  text("Leave a message in chat.", DARK_AQUA))))
                                              .clickEvent(suggestCommand("/ticket new "))
                                              .insertion("/ticket new ")));
        } else {
            player.sendMessage(text("You have " + opens.size() + " open ticket(s). Click below to view.", DARK_AQUA));
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
            sender.sendMessage(text("No open ticket(s)", YELLOW));
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(text("" + tickets.size() + " open ticket(s)", YELLOW));
        for (Ticket ticket : tickets) {
            String cmd = "/ticket view " + ticket.getId();
            NamedTextColor color = !ticket.isSilent() && (!ticket.isAssigned() || ticket.isAssigneeUpdate())
                ? YELLOW
                : GRAY;
            NamedTextColor bgcolor = GRAY;
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(text(cmd, color));
            tooltip.addAll(wrapLore(ticket.getOwnerName() + ": " + ticket.getMessage(), c -> c.color(GRAY)));
            TextComponent.Builder msg = text()
                .clickEvent(runCommand(cmd))
                .hoverEvent(showText(join(separator(newline()), tooltip)))
                .color(color)
                .append(text("[", bgcolor))
                .append(text("\u21F2", GOLD))
                .append(text(ticket.getId(), ticket.isAssigned() ? GRAY : YELLOW))
                .append(text("] ", bgcolor))
                .append(text(ticket.getOwnerName()));
            if (ticket.isAssigned()) {
                msg.append(text(" \u2192", GREEN)); // rightwards arrow
                msg.append(text(ticket.getAssigneeName(), WHITE));
            }
            msg.append(text(": ", bgcolor))
                .append(text(ticket.getShortMessage()));
            lines.add(msg.build());
        }
        sender.sendMessage(join(separator(newline()), lines));
    }

    private void viewTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.view");
        if (args.length != 1) throw new UsageException("view");
        Ticket ticket = ticketById(args[0]);
        if (!ticket.isOwner(sender)) assertPermission(sender, "ticket.view.any");
        if (!new TicketEvent(TicketEvent.Action.VIEW, ticket, sender).callEvent()) {
            return;
        }
        List<Comment> comments = db.find(Comment.class).where().eq("ticketId", ticket.getId()).orderByAscending("id").findList();
        for (Component line : ticket.getInfoLines()) {
            sender.sendMessage(line);
        }
        if (!comments.isEmpty()) {
            sender.sendMessage(textOfChildren(text(" Comments: ", DARK_AQUA),
                                              text(comments.size(), AQUA)));
            for (Comment comment : comments) {
                sender.sendMessage(textOfChildren(space(),
                                                  text(comment.getCommenterName(), AQUA),
                                                  text(": ", DARK_AQUA),
                                                  text(comment.getComment(), GRAY)));
            }
        }
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
        if (!new TicketEvent(TicketEvent.Action.CREATE, ticket, sender).callEvent()) {
            return;
        }
        db.insertAsync(ticket, result -> newTicketCallback(sender, ticket, result));
    }

    private void newTicketCallback(CommandSender sender, Ticket ticket, int insertResult) {
        new TicketEvent(TicketEvent.Action.CREATED, ticket, sender).callEvent();
        final int id = ticket.getId();
        sender.sendMessage(textOfChildren(text("Ticket", AQUA),
                                          space(),
                                          (textOfChildren(text("[", DARK_AQUA),
                                                          text("\u21f2 ", GREEN),
                                                          text(id, AQUA),
                                                          text("]", DARK_AQUA))
                                           .hoverEvent(showText(text("Click to view the ticket", DARK_AQUA)))
                                           .clickEvent(runCommand("/ticket view " + id))
                                           .insertion("/ticket view " + id)),
                                          text(" created: ", AQUA),
                                          text(ticket.getMessage(), GRAY)));
        if (!ticket.isSilent()) {
            notify(ticket.getId(), text(ticket.getOwnerName() + " created ticket [" + ticket.getId() + "]: " + ticket.getMessage(), YELLOW));
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
        if (!new TicketEvent(TicketEvent.Action.COMMENT, ticket, sender, comment).callEvent()) {
            return;
        }
        db.insertAsync(comment, result -> commentTicketCallback(sender, ticket, comment, result));
    }

    /**
     * After the comment was saved, we update the ticket itself
     * accordingly.
     */
    private void commentTicketCallback(CommandSender sender, Ticket ticket, Comment comment, int insertResult) {
        sender.sendMessage(textOfChildren(text("Commented on ticket ", AQUA),
                                          text("[", DARK_AQUA),
                                          text(ticket.getId(), AQUA),
                                          text("]", DARK_AQUA),
                                          text(": ", AQUA),
                                          text(comment.getComment(), GRAY)));
        if (ticket.isOwner(sender)) {
            ticket.setAssigneeUpdate(true);
            db.updateAsync(ticket, null, "assignee_update");
        } else {
            ticket.setUpdated(true);
            db.updateAsync(ticket, null, "updated");
            final Player owner = ticket.getOwner();
            if (owner != null) {
                owner.sendMessage(textOfChildren(text("New commented on your ticket ", DARK_AQUA),
                                                 text("[", DARK_AQUA),
                                                 text(ticket.getId(), AQUA),
                                                 text("]", DARK_AQUA),
                                                 text(" " + comment.getCommenterName(), AQUA),
                                                 text(": ", DARK_AQUA),
                                                 text(comment.getComment(), GRAY))
                                  .hoverEvent(showText(text("Click to view this ticket", DARK_AQUA)))
                                  .clickEvent(runCommand("/ticket view " + ticket.getId()))
                                  .insertion("/ticket view " + ticket.getId()));
            }
        }
        if (!ticket.isSilent()) {
            if (!ticket.isAssigned()) {
                notify(ticket.getId(), text(comment.getCommenterName() + " commented on ticket [" + comment.getTicketId() + "]: " + comment.getComment(), YELLOW));
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
        if (!new TicketEvent(TicketEvent.Action.CLOSE, ticket, sender, comment).callEvent()) {
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
                owner.sendMessage(textOfChildren(text(comment.getCommenterName() + " closed your ticket "),
                                                 text("[", DARK_AQUA),
                                                 text(ticket.getId(), AQUA),
                                                 text("]: ", DARK_AQUA),
                                                 text(comment.getComment(), GRAY))
                                  .hoverEvent(showText(text("Click to view this ticket", DARK_AQUA)))
                                  .clickEvent(runCommand("/ticket view " + ticket.getId()))
                                  .insertion("/ticket view " + ticket.getId()));
            }
        }
        sender.sendMessage(textOfChildren(text("Ticket ", AQUA),
                                          text("[", DARK_AQUA),
                                          text(ticket.getId(), AQUA),
                                          text("]", DARK_AQUA),
                                          text(" closed: ", AQUA),
                                          text(cMessage, GRAY)));
        if (!ticket.isSilent()) {
            notify(ticket.getId(), text(comment.getCommenterName() + " closed ticket [" + comment.getTicketId() + "]: " + cMessage, YELLOW));
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
        if (!new TicketEvent(TicketEvent.Action.REOPEN, ticket, sender, comment).callEvent()) {
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
                owner.sendMessage(textOfChildren(text(comment.getCommenterName() + " reopened your ticket [", DARK_AQUA),
                                                 text(ticket.getId(), AQUA),
                                                 text("]: ", DARK_AQUA),
                                                 text(comment.getComment(), GRAY))
                                  .hoverEvent(showText(text("Click to view this ticket", DARK_AQUA)))
                                  .clickEvent(runCommand("/ticket view " + ticket.getId()))
                                  .insertion("/ticket view " + ticket.getId()));
            }
        }
        sender.sendMessage(textOfChildren(text("Ticket ", AQUA),
                                          text("[", DARK_AQUA),
                                          text(ticket.getId(), AQUA),
                                          text("]", DARK_AQUA),
                                          text(" reopened: ", AQUA),
                                          text(cMessage, GRAY)));
        if (!ticket.isSilent()) {
            notify(comment.getTicketId(), text(comment.getCommenterName() + " reopened ticket [" + comment.getTicketId() + "]: " + cMessage, YELLOW));
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
        if (!new TicketEvent(TicketEvent.Action.PORT, ticket, sender).callEvent()) {
            return;
        }
        if (!getServerName().equalsIgnoreCase(ticket.getServerName())) {
            player.sendMessage(textOfChildren(text("Ticket ", AQUA),
                                              text("[", DARK_AQUA),
                                              text(ticket.getId(), AQUA),
                                              text("]", DARK_AQUA),
                                              text(" is on server " + ticket.getServerName() + "...", AQUA)));
            Bungee.send(player, ticket.getServerName());
            return;
        }
        // Try location.
        ticket.getLocation(location -> portTicketCallback(player, ticket, location));
    }

    private void portTicketCallback(final Player player, final Ticket ticket, final Location location) {
        if (location == null) {
            player.sendMessage(text("Ticket location not found.", RED));
            return;
        }
        player.teleport(location);
        player.sendMessage(textOfChildren(text("Ported to ticket ", AQUA),
                                          text("[", DARK_AQUA),
                                          text(ticket.getId(), AQUA),
                                          text("]", DARK_AQUA)));
        // Assign
        if (ticket.isAssigned()) return;
        ticket.setAssignee(player);
        ticket.setUpdated(true);
        ticket.setAssigneeUpdate(false);
        if (!new TicketEvent(TicketEvent.Action.ASSIGN, ticket, player).callEvent()) return;
        db.updateAsync(ticket, null, "assignee_name", "assignee_uuid", "updated", "assignee_update");
        Player owner = ticket.getOwner();
        if (owner != null) {
            owner.sendMessage(textOfChildren(text(player.getName() + " was assigned to your ticket [", DARK_AQUA),
                                             text(ticket.getId(), AQUA),
                                             text("]", DARK_AQUA))
                              .hoverEvent(showText(text("Click to view this ticket", DARK_AQUA)))
                              .clickEvent(runCommand("/ticket view " + ticket.getId()))
                              .insertion("/ticket view " + ticket.getId()));
        }
        if (!ticket.isSilent()) {
            notify(ticket.getId(), text(ticket.getAssigneeName() + " was assigned to ticket [" + ticket.getId() + "]", YELLOW));
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
            sender.sendMessage(text("Player not found: " + args[1], RED));
            return;
        }
        if (!Perm.get().has(playerCache.uuid, "ticket.moderation")) {
            sender.sendMessage(text("Player not a ticket moderator: " + playerCache.name, RED));
            return;
        }
        ticket.setAssignee(playerCache);
        ticket.setAssigneeUpdate(!ticket.isAssigned(sender));
        if (!new TicketEvent(TicketEvent.Action.ASSIGN, ticket, sender).callEvent()) {
            return;
        }
        db.updateAsync(ticket, null, "assignee_uuid", "assignee_name", "assignee_update");
        sender.sendMessage(textOfChildren(text("Assigned " + ticket.getAssigneeName() + " to ticket ", AQUA),
                                          text("[", DARK_AQUA),
                                          text(ticket.getId(), AQUA),
                                          text("]", DARK_AQUA)));
        Player owner = ticket.getOwner();
        if (owner != null) {
            owner.sendMessage(textOfChildren(text(ticket.getAssigneeName() + " was assigned to your ticket [", DARK_AQUA),
                                             text(ticket.getId(), AQUA),
                                             text("]", DARK_AQUA))
                              .hoverEvent(showText(text("Click to view this ticket", DARK_AQUA)))
                              .clickEvent(runCommand("/ticket view " + ticket.getId()))
                              .insertion("/ticket view " + ticket.getId()));
        }
        if (!ticket.isSilent()) {
            notify(ticket.getId(), text(sender.getName() + " assigned " + ticket.getAssigneeName() + " to ticket [" + ticket.getId() + "]", YELLOW));
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
        sender.sendMessage(text("Ticket configuration reloaded", YELLOW));
    }

    private void reminder(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.reminder");
        if (args.length > 0) throw new UsageException("reminder");
        sender.sendMessage(text("Triggering reminder...", YELLOW));
        reminder();
    }

    private void deleteTicket(CommandSender sender, String[] args) {
        assertPermission(sender, "ticket.delete");
        if (args.length != 1) throw new UsageException("delete");
        Ticket ticket = ticketById(args[0]);
        new TicketEvent(TicketEvent.Action.DELETE, ticket, sender).callEvent();
        int ticketCount = db.find(Ticket.class).eq("id", ticket.getId()).delete();
        int commentCount = db.find(Comment.class).eq("ticket_id", ticket.getId()).delete();
        sender.sendMessage(text("Deleted ticket #" + ticket.getId() + ": " + ticketCount + " tickets, " + commentCount + " comments",
                                YELLOW));
    }

    public void reminder() {
        db.find(Ticket.class)
            .eq("open", true)
            .findListAsync(this::reminderCallback);
    }

    private void reminderCallback(List<Ticket> tickets) {
        if (tickets.size() == 0) return;
        int unassigned = 0;
        int unassignedTicketId = 0;
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
                unassignedTicketId = ticket.getId();
            }
            if (ticket.isUpdated() && ticket.getOwnerUuid() != null) {
                ownerUpdates.put(ticket.getOwnerUuid(), ticket);
            }
        }
        if (unassigned > 1) {
            notify(text("There are " + unassigned + " unassigned tickets. Please attend to them.", YELLOW));
        } else if (unassigned == 1) {
            notify(unassignedTicketId, text("There is an unassigned ticket. Please attend to it.", YELLOW));
        }
        for (Map.Entry<UUID, Ticket> entry : adminUpdates.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Ticket ticket = entry.getValue();
            String cmd = "/ticket view " + ticket.getId();
            player.sendMessage(text()
                               .content("Ticket [")
                               .color(YELLOW)
                               .append(text("" + ticket.getId(), GOLD))
                               .append(text("] by " + ticket.getOwnerName() + " has an update! Click for more info."))
                               .hoverEvent(showText(text(cmd, YELLOW)))
                               .clickEvent(runCommand(cmd)));
        }
        for (Map.Entry<UUID, Ticket> entry : ownerUpdates.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) continue;
            Ticket ticket = entry.getValue();
            String cmd = "/ticket view " + ticket.getId();
            player.sendMessage(text()
                               .content("Ticket [")
                               .color(DARK_AQUA)
                               .append(text("" + ticket.getId(), AQUA))
                               .append(text("] has an update for you! Click here for more info."))
                               .hoverEvent(showText(text(cmd, YELLOW)))
                               .clickEvent(runCommand(cmd)));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("ticket.moderation")) {
            db.find(Ticket.class).where().eq("open", true).isNull("assignee_name").findRowCountAsync(unassigned -> {
                    if (unassigned > 1) {
                        notify(text("There are " + unassigned + " unassigned tickets. Please attend to them.", YELLOW));
                    } else if (unassigned == 1) {
                        notify(text("There is an unassigned ticket. Please attend to it.", YELLOW));
                    }
                });
        }
        db.find(Ticket.class).where().eq("ownerUuid", player.getUniqueId()).eq("updated", true).findRowCountAsync((tickets) -> {
                if (tickets <= 0) return;
                player.sendMessage(textOfChildren(text("There are ticket updates for you. ", DARK_AQUA),
                                                  text("[", DARK_AQUA),
                                                  text("Click Here", AQUA),
                                                  text("] for more info.", DARK_AQUA))
                                   .hoverEvent(showText(text("Click here for more info", DARK_AQUA)))
                                   .clickEvent(runCommand(("/ticket")))
                                   .insertion("/ticket"));
            });
    }

    public void notify(Component message) {
        message = message
            .hoverEvent(showText(text("Click to view tickets", DARK_AQUA)))
            .clickEvent(runCommand("/ticket"))
            .insertion("/ticket");
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasPermission("ticket.notify")) continue;
            player.sendMessage(message);
        }
    }

    public void notify(int id, Component message) {
        message = message
            .hoverEvent(showText(text("Click to view the ticket", DARK_AQUA)))
            .clickEvent(runCommand("/ticket view " + id))
            .insertion("/ticket view " + id);
        for (Player player : getServer().getOnlinePlayers()) {
            if (!player.hasPermission("ticket.notify")) continue;
            player.sendMessage(message);
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
