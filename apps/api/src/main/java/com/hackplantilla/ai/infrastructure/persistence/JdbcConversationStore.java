package com.hackplantilla.ai.infrastructure.persistence;

import com.hackplantilla.ai.AiModule;
import com.hackplantilla.ai.domain.ChatMessage;
import com.hackplantilla.ai.domain.ConversationStore;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@AiModule
class JdbcConversationStore implements ConversationStore {

    private final JdbcTemplate jdbc;

    JdbcConversationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ChatMessage> history(UUID conversationId) {
        return jdbc.query(
                "SELECT role, content FROM ai_messages WHERE conversation_id = ? ORDER BY id",
                (rs, row) -> new ChatMessage(rs.getString("role"), rs.getString("content")),
                conversationId);
    }

    @Override
    public void append(UUID conversationId, ChatMessage message) {
        jdbc.update("INSERT INTO ai_messages (conversation_id, role, content) VALUES (?, ?, ?)",
                conversationId, message.role(), message.content());
    }
}
