package com.winthier.ticket.sql;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import lombok.Data;

@Data
@Table(name = "webhook")
public final class SQLWebhook {
    @Id
    private Integer id;
    @Column(nullable = true, length = 255)
    private String url;
}
