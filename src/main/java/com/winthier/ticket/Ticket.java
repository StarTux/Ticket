package com.winthier.ticket;

import com.cavetale.core.playercache.PlayerCache;
import com.cavetale.core.worlds.Worlds;
import com.winthier.sql.SQLRow;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;
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

@Entity @Getter @Setter
@Table(name = "tickets",
       indexes = {
           @Index(name = "idx_owner", columnList = "owner_uuid"),
           @Index(name = "idx_assignee", columnList = "assignee_uuid"),
           @Index(name = "idx_open", columnList = "open"),
       })
public final class Ticket implements SQLRow {
    @Id
    private Integer id;

    private UUID ownerUuid;

    @Column(nullable = false, length = 16)
    private String ownerName;

    @Column(nullable = false)
    private String serverName;

    @Column(nullable = false)
    private String worldName;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double z;

    @Column(nullable = false)
    private float pitch;

    @Column(nullable = false)
    private float yaw;

    @Column(nullable = false)
    private String message;

    private UUID assigneeUuid;
    private String assigneeName;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean assigneeUpdate;

    private Date createTime;

    @Column(nullable = false)
    private boolean open = true;

    @Column(nullable = false)
    private boolean updated;

    @Column(nullable = false, columnDefinition = "TINYINT(1) NOT NULL DEFAULT 1")
    private boolean silent;

    public Ticket() { }

    public Ticket(final String serverName, final Player owner, final String message) {
        this.serverName = serverName;
        setOwner(owner);
        setLocation(owner.getLocation());
        this.message = message;
        this.createTime = new Date();
        this.assigneeUpdate = true;
    }

    public void setOwner(Player player) {
        setOwnerUuid(player.getUniqueId());
        setOwnerName(player.getName());
    }

    public Player getOwner() {
        return ownerUuid != null ? Bukkit.getServer().getPlayer(ownerUuid) : null;
    }

    public boolean isOwner(CommandSender sender) {
        if (ownerUuid != null && sender instanceof Player) {
            return ownerUuid.equals(((Player) sender).getUniqueId());
        }
        return sender.getName().equalsIgnoreCase(ownerName);
    }

    public String getOwnerName() {
        if (ownerUuid != null) {
            String result = PlayerCache.nameForUuid(ownerUuid);
            if (result != null) return result;
        }
        return ownerName;
    }

    public String getAssigneeName() {
        if (assigneeUuid != null) {
            String result = PlayerCache.nameForUuid(assigneeUuid);
            if (result != null) return result;
        }
        return assigneeName;
    }

    public Player getAssignee() {
        return assigneeUuid != null ? Bukkit.getServer().getPlayer(assigneeUuid) : null;
    }

    /**
     * @return the location or null if world not found
     */
    public void getLocation(Consumer<Location> callback) {
        Worlds.get().loadWorld(worldName, world -> {
                callback.accept(world != null
                                ? new Location(world, x, y, z, yaw, pitch)
                                : null);
            });
    }

    public void setLocation(Location location) {
        setWorldName(location.getWorld().getName());
        setX(location.getX());
        setY(location.getY());
        setZ(location.getZ());
        setPitch(location.getPitch());
        setYaw(location.getYaw());
    }

    public void setAssignee(Player player) {
        setAssigneeUuid(player.getUniqueId());
        setAssigneeName(player.getName());
    }

    public void setAssignee(PlayerCache playerCache) {
        setAssigneeUuid(playerCache.uuid);
        setAssigneeName(playerCache.name);
    }

    public boolean isAssigned() {
        return assigneeName != null;
    }

    public boolean isAssigned(Player player) {
        return player.getUniqueId().equals(assigneeUuid);
    }

    public boolean isAssigned(CommandSender sender) {
        return sender instanceof Player && ((Player) sender).getUniqueId().equals(assigneeUuid);
    }

    public void sendShortInfo(CommandSender sender, boolean verbose) {
        if (sender instanceof Player) {
            String infoMessage = this.getMessage();
            if (infoMessage.length() > 24) {
                infoMessage = infoMessage.substring(0, 24) + "...";
            }
            List<Object> json = new ArrayList<>();
            json.add(" ");
            json.add(Util.commandRunButton("&3[&a\u21F2 &b" + id + "&3]", "&3Click to view this ticket", "/ticket view " + id));
            if (verbose) {
                json.add(Util.format(" &3(&b%s&3)", serverName));
            }
            if (assigneeName == null) {
                json.add(Util.format(" &b%s&3:&7 %s", ownerName, infoMessage));
            } else {
                json.add(Util.format(" &b%s&3 => &b%s&3: &7%s", ownerName, assigneeName, infoMessage));
            }
            Util.tellRaw((Player) sender, json);
        } else {
            String infoMessage = this.message;
            if (infoMessage.length() > 24) {
                infoMessage = infoMessage.substring(0, 24) + "...";
            }
            if (assigneeName == null) {
                Util.sendMessage(sender, "&3[&b%d&3] (&b%s&3) &b%s&3:&7 %s", id, serverName, ownerName, infoMessage);
            } else {
                Util.sendMessage(sender, "&3[&b%d&3] (&b%s&3) &b%s&3 => &b%s&3:&7 %s", id, serverName, ownerName, assigneeName, infoMessage);
            }
        }
    }

    public String getShortMessage() {
        final int max = 18;
        return message.length() > max
            ? message.substring(0, max - 2) + "..."
            : message;
    }

    public void sendOptions(CommandSender sender) {
        if (!(sender instanceof Player player)) return;
        final List<Component> buttons = new ArrayList<>();
        if (sender.hasPermission("ticket.view") && (sender.hasPermission("ticket.view.any") || isOwner(sender))) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u21bb", GREEN), text(" Refresh", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(text("Click to refresh the view", DARK_AQUA)))
                        .clickEvent(runCommand("/ticket view " + id))
                        .insertion("/ticket view " + id));
        }
        if (isOpen() && sender.hasPermission("ticket.comment") && (sender.hasPermission("ticket.comment.any") || isOwner(sender))) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u270e", YELLOW), text(" Comments", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(textOfChildren(text("Click to comment on this ticket.", DARK_AQUA),
                                                            newline(),
                                                            text("Leave your message in chat.", DARK_AQUA))))
                        .clickEvent(suggestCommand("/ticket comment " + id + " "))
                        .insertion("/ticket comment " + id + " "));
        }
        if (isOpen() && sender.hasPermission("ticket.assign")) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u27a5", BLUE), text(" Assign", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(textOfChildren(text("Click to assign this ticket.", DARK_AQUA),
                                                            newline(),
                                                            text("Type the target in chat.", DARK_AQUA))))
                        .clickEvent(suggestCommand("/ticket assign " + id + " "))
                        .insertion("/ticket assign " + id + " "));
        }
        if (isOpen() && sender.hasPermission("ticket.close") && (sender.hasPermission("ticket.close.any") || isOwner(sender))) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u2716", DARK_RED), text(" Close", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(textOfChildren(text("Click to close this ticket.", DARK_AQUA),
                                                            newline(),
                                                            text("Don't forget to type a reason.", DARK_AQUA))))
                        .clickEvent(suggestCommand("/ticket close " + id + " "))
                        .insertion("/ticket close " + id + " "));
        }
        if (!isOpen() && sender.hasPermission("ticket.reopen") && (sender.hasPermission("ticket.reopen.any") || isOwner(sender))) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u2672", DARK_GRAY), text(" Reopen", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(textOfChildren(text("Click to re-open this ticket.", DARK_AQUA),
                                                            newline(),
                                                            text("Don't forget to type a reason.", DARK_AQUA))))
                        .clickEvent(suggestCommand("/ticket reopen " + id + " "))
                        .insertion("/ticket reopen " + id + " "));
        }
        if (sender.hasPermission("ticket.port")) {
            buttons.add(textOfChildren(text("[", DARK_AQUA),
                                       text("\u21b7", YELLOW), text(" Port", AQUA),
                                       text("]", DARK_AQUA))
                        .hoverEvent(showText(text("Click to port to this ticket.", DARK_AQUA)))
                        .clickEvent(runCommand("/ticket port " + id))
                        .insertion("/ticket port " + id));
        }
        if (buttons.isEmpty()) return;
        player.sendMessage(textOfChildren(space(), join(separator(space()), buttons)));
    }

    public String getInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(Util.format("&3Ticket &b%d", id));
        sb.append(Util.format("\n&3 Owner: &b%s", ownerName));
        sb.append(Util.format("\n&3 Created: &b%s &3(%s ago)", Util.formatDate(createTime.getTime()),
                              Util.formatInterval(System.currentTimeMillis() - createTime.getTime())));
        sb.append(Util.format("\n&3 Message: &b%s", message));
        if (isAssigned()) {
            sb.append(Util.format("\n&3 Assigned to: &b%s", assigneeName));
        }
        sb.append(Util.format("\n&3 Location: (&b%s&3) &b%s &b%d&3,&b%d&3,&b%d", serverName, worldName,
                              NumberConversions.floor(x), NumberConversions.floor(y), NumberConversions.floor(z)));
        sb.append(Util.format("\n&3 Status: &b%s", (isOpen() ? "Open" : "Closed")));
        return sb.toString();
    }
}
