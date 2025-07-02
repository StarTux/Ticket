package com.winthier.ticket;

import com.cavetale.core.playercache.PlayerCache;
import com.winthier.sql.SQLRow;
import java.util.Date;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Entity @Getter @Setter
@Table(name = "comments",
       indexes = {
           @Index(name = "idx_ticket", columnList = "ticket_id"),
       })
public final class Comment implements SQLRow {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer ticketId;

    private UUID commenterUuid;

    @Column(nullable = false, length = 16)
    private String commenterName;

    @Column
    private String comment;

    private Date createTime;

    public Comment() { }

    public Comment(final Integer ticketId, final CommandSender commenter, final String comment) {
        setTicketId(ticketId);
        if (commenter instanceof Player) setCommenterUuid(((Player) commenter).getUniqueId());
        setCommenterName(commenter.getName());
        setComment(comment);
        setCreateTime(new Date());
    }

    public String getCommenterName() {
        if (commenterUuid != null) {
            String result = PlayerCache.nameForUuid(commenterUuid);
            if (result != null) return result;
        }
        return commenterName;
    }
}
