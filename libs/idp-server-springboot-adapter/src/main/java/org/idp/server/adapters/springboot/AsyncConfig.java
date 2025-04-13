package org.idp.server.adapters.springboot;

import org.idp.server.adapters.springboot.event.SecurityEventRetryScheduler;
import org.idp.server.adapters.springboot.event.SecurityEventRunnable;
import org.idp.server.core.security.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  Logger logger = LoggerFactory.getLogger(AsyncConfig.class);
  SecurityEventRetryScheduler retryScheduler;

  public AsyncConfig(SecurityEventRetryScheduler retryScheduler) {
    this.retryScheduler = retryScheduler;
  }

  @Bean("securityEventTaskExecutor")
  public TaskExecutor securityEventTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("SecurityEvent-Async-");

    executor.setRejectedExecutionHandler(
        (r, executor1) -> {
          logger.warn("Rejected Execution Handler");

          if (r instanceof SecurityEventRunnable) {
            SecurityEvent securityEvent = ((SecurityEventRunnable) r).getEvent();
            retryScheduler.enqueue(securityEvent);
          } else {

            logger.error("unknown EventRunnable" + r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }
}
