package com.winthier.ticket;

import com.winthier.playercache.PlayerCache;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

@Entity @Getter @Setter @Table(name = "tickets")
public final class Ticket {
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

    private Date createTime;

    @Column(nullable = false)
    private boolean open = true;

    @Column(nullable = false)
    private boolean updated;

    @Column(nullable = false, columnDefinition = "INT(1) NOT NULL DEFAULT 1")
    private boolean silent;

    public Ticket() { }

    public Ticket(final String serverName, final Player owner, final String message) {
        setServerName(serverName);
        setOwner(owner);
        setLocation(owner.getLocation());
        setMessage(message);
        setCreateTime(new Date());
    }

    public void setOwner(Player player) {
        setOwnerUuid(player.getUniqueId());
        setOwnerName(player.getName());
    }

    public Player getOwner() {
        if (ownerUuid != null) return Bukkit.getServer().getPlayer(ownerUuid);
        return Bukkit.getServer().getPlayerExact(ownerName);
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

    /**
     * @return the location or null if world not found
     */
    public Location getLocation() {
        World world = Bukkit.getServer().getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
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

    public boolean isAssigned() {
        return assigneeName != null;
    }

    public boolean isAssigned(Player player) {
        if (assigneeUuid != null) return player.getUniqueId().equals(assigneeUuid);
        return player.getName().equalsIgnoreCase(assigneeName);
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

    public void sendOptions(CommandSender sender) {
        if (!(sender instanceof Player)) return;
        Player player = (Player) sender;
        List<Object> json = new ArrayList<>();
        if (sender.hasPermission("ticket.view") && (sender.hasPermission("ticket.view.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandRunButton("&3[&a\u21BB &bRefresh&3]", "&3Click to refresh the view", "/ticket view " + id));
        }
        if (isOpen() && sender.hasPermission("ticket.comment") && (sender.hasPermission("ticket.comment.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&a\u270E &bComment&3]",
                                               "&3Click to comment on this ticket.\n&3Leave your message in chat.",
                                               "/ticket comment " + id + " "));
        }
        if (isOpen() && sender.hasPermission("ticket.assign")) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&9\u27A5 &bAssign&3]",
                                               "&3Click to assign this ticket.\n&3Type the target in chat.",
                                               "/ticket assign " + id + " "));
        }
        if (isOpen() && sender.hasPermission("ticket.close") && (sender.hasPermission("ticket.close.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&4\u2716 &bClose&3]",
                                               "&3Click to close this ticket.\n&3Don't forget to type a reason.",
                                               "/ticket close " + id + " "));
        }
        if (!isOpen() && sender.hasPermission("ticket.reopen") && (sender.hasPermission("ticket.reopen.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&8\u2672 &bReopen&3]",
                                               "&3Click to re-open this ticket.\n&3Don't forget to type a reason.",
                                               "/ticket reopen " + id + " "));
        }
        if (sender.hasPermission("ticket.port")) {
            json.add(" ");
            json.add(Util.commandRunButton("&3[&e\u21B7 &bPort&3]",
                                           "&3Click to port to this ticket.",
                                           "/ticket port " + id));
        }
        if (json.isEmpty()) return;
        Util.tellRaw(player, json);
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
