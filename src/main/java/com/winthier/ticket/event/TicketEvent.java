package com.winthier.ticket.event;

import com.winthier.ticket.Comment;
import com.winthier.ticket.Ticket;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter @RequiredArgsConstructor
public final class TicketEvent extends Event implements Cancellable {
    private final Action action;
    private final Ticket ticket;
    private final CommandSender sender;
    private final Player player;
    private final Comment comment;
    @Setter private boolean cancelled;
    /**
     * Required by Event.
     */
    @Getter private static HandlerList handlerList = new HandlerList();

    /**
     * Required by Event.
     */
    @Override public HandlerList getHandlers() {
        return handlerList;
    }

    public TicketEvent(final Action action, final Ticket ticket, final CommandSender sender, final Comment comment) {
        this(action,
             ticket,
             sender,
             sender instanceof Player ? (Player) sender : null,
             comment);
    }

    public TicketEvent(final Action action, final Ticket ticket, final CommandSender sender) {
        this(action, ticket, sender, (Comment) null);
    }

    public enum Action {
        CREATE,
        CREATED,
        COMMENT,
        PORT,
        ASSIGN,
        CLOSE,
        REOPEN,
        VIEW,
        DELETE;
    }

    public boolean hasComment() {
        return comment != null;
    }

    public boolean hasPlayer() {
        return player != null;
    }

    public boolean call() {
        Bukkit.getPluginManager().callEvent(this);
        return !cancelled;
    }
}
