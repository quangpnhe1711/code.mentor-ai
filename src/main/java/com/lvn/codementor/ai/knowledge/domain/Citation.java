package com.lvn.codementor.ai.knowledge.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "citations")
public class Citation extends BaseEntity {

    @Column(name = "chat_message_id", nullable = false)
    private UUID chatMessageId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "line_start")
    private Integer lineStart;

    @Column(name = "line_end")
    private Integer lineEnd;

    @Column(name = "reason", nullable = false)
    private String reason;

    protected Citation() {
        // for JPA
    }

    public Citation(UUID chatMessageId, String filePath, Integer lineStart, Integer lineEnd, String reason) {
        this.chatMessageId = chatMessageId;
        this.filePath = filePath;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.reason = reason;
    }

    public UUID getChatMessageId() {
        return chatMessageId;
    }

    public String getFilePath() {
        return filePath;
    }

    public Integer getLineStart() {
        return lineStart;
    }

    public Integer getLineEnd() {
        return lineEnd;
    }

    public String getReason() {
        return reason;
    }
}
