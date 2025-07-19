package org.idp.server;

import io.lettuce.core.RedisConnectionException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(20)
public class RedisSessionFallbackFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (RedisConnectionFailureException | RedisConnectionException ex) {
            // Redis死んどる時はログだけ出して無視する
            LoggerFactory.getLogger(getClass()).warn("Redis unreachable, proceeding without session", ex);

            // 新しいリクエストチェーンで再実行（セッションなし）
            HttpServletRequest wrapper = new HttpServletRequestWrapper(request) {
                @Override
                public HttpSession getSession(boolean create) {
                    return null;
                }

                @Override
                public HttpSession getSession() {
                    return null;
                }
            };
            filterChain.doFilter(wrapper, response);
        }
    }
}
