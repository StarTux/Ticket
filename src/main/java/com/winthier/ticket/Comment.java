package com.winthier.ticket;

import com.avaje.ebean.annotation.CreatedTimestamp;
import com.avaje.ebean.validation.Length;
import com.avaje.ebean.validation.NotEmpty;
import com.avaje.ebean.validation.NotNull;
import java.sql.Timestamp;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import org.bukkit.command.CommandSender;

@Entity()
@Table(name = "comments")
public class Comment {
    @Id
    private Integer id;

    @NotNull
    private Integer ticketId;

    @Length(max = 16)
    @NotEmpty
    private String commenterName;

    @NotEmpty
    private String comment;

    @CreatedTimestamp
    private Timestamp createTime;

    public Comment() {}

    public Comment(Integer ticketId, CommandSender commenter, String comment) {
        setTicketId(ticketId);
        setCommenterName(commenter.getName());
        setComment(comment);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getTicketId() {
        return ticketId;
    }

    public void setTicketId(Integer ticketId) {
        this.ticketId = ticketId;
    }

    public String getCommenterName() {
        return commenterName;
    }

    public void setCommenterName(String commenterName) {
        this.commenterName = commenterName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Timestamp getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Timestamp createTime) {
        this.createTime = createTime;
    }

    public String getInfo() {
        return Util.format("&b%s&3:&7 %s", commenterName, comment);
    }
}
