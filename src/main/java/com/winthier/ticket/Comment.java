package com.winthier.ticket;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;

@Entity @Getter @Setter @Table(name = "comments")
public final class Comment {
    @Id
    private Integer id;

    @Column(nullable = false)
    private Integer ticketId;

    @Column(nullable = false, length = 16)
    private String commenterName;

    @Column
    private String comment;

    private Date createTime;

    public Comment() { }

    public Comment(Integer ticketId, CommandSender commenter, String comment) {
        setTicketId(ticketId);
        setCommenterName(commenter.getName());
        setComment(comment);
        setCreateTime(new Date());
    }

    public String getInfo() {
        return Util.format("&b%s&3:&7 %s", commenterName, comment);
    }
}
