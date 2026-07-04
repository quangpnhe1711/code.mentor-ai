package com.lvn.codementor.ai.knowledge.domain;

import com.lvn.codementor.ai.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessage extends BaseEntity {

    @Column(name = "chat_session_id", nullable = false)
    private UUID chatSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private ChatMessageRole role;

    @Column(name = "content", nullable = false)
    private String content;

    protected ChatMessage() {
        // for JPA
    }

    public ChatMessage(UUID chatSessionId, ChatMessageRole role, String content) {
        this.chatSessionId = chatSessionId;
        this.role = role;
        this.content = content;
    }

    public UUID getChatSessionId() {
        return chatSessionId;
    }

    public ChatMessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }
}
