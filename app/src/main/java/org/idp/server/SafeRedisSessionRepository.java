package org.idp.server;

import org.idp.server.platform.log.LoggerWrapper;
import org.springframework.boot.web.servlet.server.Session;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;

public class SafeRedisSessionRepository extends RedisIndexedSessionRepository {

    LoggerWrapper log = LoggerWrapper.getLogger(RedisIndexedSessionRepository.class);

    public SafeRedisSessionRepository(RedisOperations<String, Object> sessionRedisOperations) {
        super(sessionRedisOperations);
    }

    @Override
    public void save(RedisSession session) {
        try {
            super.save(session);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis unreachable, skipping session save", ex);
        }
    }

    // 他にも deleteById / findById などもwrapする
}

