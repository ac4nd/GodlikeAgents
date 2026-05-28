package com.hypersense.boot.framework.agents.memory;

import com.hypersense.boot.framework.agents.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆存储与检索（JdbcTemplate + pgvector）
 *
 * @author Claude
 * @since 2026/5/27
 */
@Slf4j
@RequiredArgsConstructor
public class MemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AgentProperties.MemoryConfig config;

    private static final String VECTOR_CAST = "?::vector";

    public void initialize() {
        log.info("MemoryRepository: 初始化 agent_memory 表（embedding 维度={}）", config.getEmbeddingDimensions());
        jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS agent_memory (
                    id              BIGSERIAL PRIMARY KEY,
                    tenant_id       BIGINT NOT NULL,
                    user_id         BIGINT NOT NULL,
                    content         TEXT NOT NULL,
                    category        VARCHAR(32) DEFAULT 'fact',
                    embedding       vector(%d),
                    session_id      VARCHAR(64),
                    access_count    INT DEFAULT 0,
                    created_at      TIMESTAMP DEFAULT NOW(),
                    last_accessed_at TIMESTAMP DEFAULT NOW()
                )
                """.formatted(config.getEmbeddingDimensions()));
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_memory_user ON agent_memory(user_id)");
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_embedding
                ON agent_memory USING ivfflat (embedding vector_cosine_ops)
                WITH (lists = 100)
                """);
    }

    public void store(AgentMemory memory) {
        jdbcTemplate.update("""
                INSERT INTO agent_memory (tenant_id, user_id, content, category, embedding, session_id)
                VALUES (?, ?, ?, ?, %s, ?)
                """.formatted(VECTOR_CAST),
                memory.getTenantId(), memory.getUserId(),
                memory.getContent(), memory.getCategory(),
                toVectorLiteral(memory.getEmbedding()),
                memory.getSessionId());
    }

    /**
     * 向量相似度搜索（带 tenant_id 隔离 + threshold 过滤）
     */
    public List<AgentMemory> search(Long tenantId, Long userId, float[] queryEmbedding,
                                    int limit, double threshold) {
        String vectorLiteral = toVectorLiteral(queryEmbedding);
        return jdbcTemplate.query("""
                SELECT id, tenant_id, user_id, content, category, session_id,
                       access_count, created_at, last_accessed_at
                FROM agent_memory
                WHERE tenant_id = ?
                  AND user_id = ?
                  AND embedding IS NOT NULL
                  AND 1 - (embedding <=> %s) >= ?
                ORDER BY embedding <=> %s
                LIMIT ?
                """.formatted(VECTOR_CAST, VECTOR_CAST),
                memoryRowMapper(),
                tenantId, userId, vectorLiteral, threshold, vectorLiteral, limit);
    }

    public List<AgentMemory> searchByKeyword(Long tenantId, Long userId, String keyword, int limit) {
        return jdbcTemplate.query("""
                SELECT id, tenant_id, user_id, content, category, session_id,
                       access_count, created_at, last_accessed_at
                FROM agent_memory
                WHERE tenant_id = ?
                  AND user_id = ?
                  AND content ILIKE ?
                ORDER BY access_count DESC, created_at DESC
                LIMIT ?
                """, memoryRowMapper(), tenantId, userId, "%" + keyword + "%", limit);
    }

    /**
     * 批量递增访问计数（避免 N+1）
     */
    public void batchIncrementAccessCount(List<Long> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        String placeholders = String.join(",", memoryIds.stream().map(id -> "?").toList());
        jdbcTemplate.update("""
                UPDATE agent_memory
                SET access_count = access_count + 1, last_accessed_at = NOW()
                WHERE id IN (%s)
                """.formatted(placeholders), memoryIds.toArray());
    }

    public int deleteOlderThan(int days) {
        return jdbcTemplate.update("""
                DELETE FROM agent_memory
                WHERE created_at < NOW() - INTERVAL '1 day' * ?
                """, days);
    }

    // ========== 内部方法 ==========

    private RowMapper<AgentMemory> memoryRowMapper() {
        return (rs, rowNum) -> AgentMemory.builder()
                .id(rs.getLong("id"))
                .tenantId(rs.getLong("tenant_id"))
                .userId(rs.getLong("user_id"))
                .content(rs.getString("content"))
                .category(rs.getString("category"))
                .sessionId(rs.getString("session_id"))
                .accessCount(rs.getInt("access_count"))
                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                .lastAccessedAt(rs.getTimestamp("last_accessed_at").toLocalDateTime())
                .build();
    }

    private String toVectorLiteral(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
