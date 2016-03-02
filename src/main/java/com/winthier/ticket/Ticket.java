package com.winthier.ticket;

import com.avaje.ebean.annotation.CreatedTimestamp;
import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotEmpty;
import com.avaje.ebean.validation.NotNull;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.NumberConversions;

@Entity()
@Table(name = "tickets")
public class Ticket {
    @Id
    private Integer id;

    @NotEmpty
    @Length(max = 16)
    private String ownerName;

    @NotEmpty
    private String serverName;

    @NotEmpty
    private String worldName;

    @NotNull
    private double x;

    @NotNull
    private double y;

    @NotNull
    private double z;

    @NotNull
    private float pitch;

    @NotNull
    private float yaw;

    @NotNull
    private String message;

    private String assigneeName;

    @CreatedTimestamp
    private Timestamp createTime;

    @NotNull
    private boolean open = true;

    @NotNull
    private boolean updated;

    public Ticket() {}

    public Ticket(String serverName, Player owner, String message) {
        setServerName(serverName);
        setOwner(owner);
        setLocation(owner.getLocation());
        setMessage(message);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public void setOwner(Player player) {
        setOwnerName(player.getName());
    }

    public Player getOwner() {
        return Bukkit.getServer().getPlayerExact(ownerName);
    }

    public boolean isOwner(CommandSender sender) {
        return sender.getName().equalsIgnoreCase(ownerName);
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public double getZ() {
        return z;
    }

    public void setZ(double z) {
        this.z = z;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public void setYaw(float yaw) {
        this.yaw = yaw;
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAssigneeName() {
        return assigneeName;
    }

    public void setAssigneeName(String assigneeName) {
        this.assigneeName = assigneeName;
    }

    public void setAssignee(CommandSender sender) {
        this.assigneeName = sender.getName();
    }

    public boolean isAssigned() {
        return assigneeName != null;
    }

    public boolean isAssigned(CommandSender sender) {
        return sender.getName().equalsIgnoreCase(assigneeName);
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean isUpdated() {
        return updated;
    }

    public void setUpdated(boolean updated) {
        this.updated = updated;
    }

    public void sendShortInfo(CommandSender sender, boolean verbose) {
        if (sender instanceof Player) {
            String message = this.getMessage();
            if (message.length() > 24) {
                message = message.substring(0, 24) + "...";
            }
            List<Object> json = new ArrayList<>();
            json.add(" ");
            json.add(Util.commandRunButton("&3[&a\u21F2 &b"+id+"&3]", "&3Click to view this ticket", "/ticket view "+id));
            if (verbose) {
                json.add(Util.format(" &3(&b%s&3)", serverName));
            }
            if (assigneeName == null) {
                json.add(Util.format(" &b%s&3:&7 %s", ownerName, message));
            } else {
                json.add(Util.format(" &b%s&3 => &b%s&3: &7%s", ownerName, assigneeName, message));
            }
            Util.tellRaw((Player)sender, json);
        } else {
            String message = this.message;
            if (message.length() > 24) {
                message = message.substring(0, 24) + "...";
            }
            if (assigneeName == null) {
                Util.sendMessage(sender, "&3[&b%d&3] (&b%s&3) &b%s&3:&7 %s", id, serverName, ownerName, message);
            } else {
                Util.sendMessage(sender, "&3[&b%d&3] (&b%s&3) &b%s&3 => &b%s&3:&7 %s", id, serverName, ownerName, assigneeName, message);
            }
        }
    }

    public void sendOptions(CommandSender sender) {
        if (!(sender instanceof Player)) return;
        Player player = (Player)sender;
        List<Object> json = new ArrayList<>();
        int id = getId();
        if (sender.hasPermission("ticket.view") && (sender.hasPermission("ticket.view.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandRunButton("&3[&a\u21BB &bRefresh&3]", "&3Click to refresh the view", "/ticket view "+id));
        }
        if (isOpen() && sender.hasPermission("ticket.comment") && (sender.hasPermission("ticket.comment.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&a\u270E &bComment&3]", "&3Click to comment on this ticket.\n&3Leave your message in chat.", "/ticket comment "+id+" "));
        }
        if (isOpen() && sender.hasPermission("ticket.assign")) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&9\u27A5 &bAssign&3]", "&3Click to assign this ticket.\n&3Type the target in chat.", "/ticket assign "+id+" "));
        }
        if (isOpen() && sender.hasPermission("ticket.close") && (sender.hasPermission("ticket.close.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&4\u2716 &bClose&3]", "&3Click to close this ticket.\n&3Don't forget to type a reason.", "/ticket close "+id+" "));
        }
        if (!isOpen() && sender.hasPermission("ticket.reopen") && (sender.hasPermission("ticket.reopen.any") || isOwner(sender))) {
            json.add(" ");
            json.add(Util.commandSuggestButton("&3[&8\u2672 &bReopen&3]", "&3Click to re-open this ticket.\n&3Don't forget to type a reason.", "/ticket reopen "+id+" "));
        }
        if (sender.hasPermission("ticket.port")) {
            json.add(" ");
            json.add(Util.commandRunButton("&3[&e\u21B7 &bPort&3]", "&3Click to port to this ticket.", "/ticket port "+id));
        }
        if (json.isEmpty()) return;
        Util.tellRaw(player, json);
    }

    public String getInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append(Util.format("&3Ticket &b%d", id));
        sb.append(Util.format("\n&3 Owner: &b%s", ownerName));
        sb.append(Util.format("\n&3 Created: &b%s &3(%s ago)", Util.formatDate(createTime.getTime()), Util.formatInterval(System.currentTimeMillis() - createTime.getTime())));
        sb.append(Util.format("\n&3 Message: &b%s", message));
        if (isAssigned()) {
            sb.append(Util.format("\n&3 Assigned to: &b%s", assigneeName));
        }
        sb.append(Util.format("\n&3 Location: (&b%s&3) &b%s &b%d&3,&b%d&3,&b%d", serverName, worldName, NumberConversions.floor(x), NumberConversions.floor(y), NumberConversions.floor(z)));
        sb.append(Util.format("\n&3 Status: &b%s", (isOpen() ? "Open" : "Closed")));
        return sb.toString();
    }

    public boolean notifyOwner(String message, Object... args) {
        Player owner = getOwner();
        if (owner == null) return false;
        Util.sendMessage(owner, message, args);
        return true;
    }
}
