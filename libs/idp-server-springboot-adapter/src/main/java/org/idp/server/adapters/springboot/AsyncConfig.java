/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.adapters.springboot;

import org.idp.server.adapters.springboot.application.event.*;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(AsyncProperties.class)
public class AsyncConfig {

  LoggerWrapper logger = LoggerWrapper.getLogger(AsyncConfig.class);
  SecurityEventRetryScheduler securityEventRetryScheduler;
  UserLifecycleEventRetryScheduler userLifecycleEventRetryScheduler;
  AuditLogRetryScheduler auditLogRetryScheduler;
  AsyncProperties asyncProperties;

  public AsyncConfig(
      SecurityEventRetryScheduler securityEventRetryScheduler,
      UserLifecycleEventRetryScheduler userLifecycleEventRetryScheduler,
      AuditLogRetryScheduler auditLogRetryScheduler,
      AsyncProperties asyncProperties) {
    this.securityEventRetryScheduler = securityEventRetryScheduler;
    this.userLifecycleEventRetryScheduler = userLifecycleEventRetryScheduler;
    this.auditLogRetryScheduler = auditLogRetryScheduler;
    this.asyncProperties = asyncProperties;
  }

  @Bean("securityEventTaskExecutor")
  public TaskExecutor securityEventTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.ExecutorProperties props = asyncProperties.getSecurityEvent();
    executor.setCorePoolSize(props.getCorePoolSize());
    executor.setMaxPoolSize(props.getMaxPoolSize());
    executor.setQueueCapacity(props.getQueueCapacity());
    executor.setThreadNamePrefix("SecurityEvent-Async-");

    executor.setRejectedExecutionHandler(
        (r, executorRef) -> {
          if (r instanceof SecurityEventRunnable) {
            SecurityEvent securityEvent = ((SecurityEventRunnable) r).getEvent();
            logger.warn(
                "security event rejected, queuing for retry: id={}, type={}, pool=[active={}, queue={}, completed={}]",
                securityEvent.identifier().value(),
                securityEvent.type().value(),
                executor.getActiveCount(),
                executor.getThreadPoolExecutor().getQueue().size(),
                executor.getThreadPoolExecutor().getCompletedTaskCount());
            securityEventRetryScheduler.enqueue(securityEvent);
          } else {
            logger.error(
                "unknown runnable rejected from security event executor: {}",
                r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }

  @Bean("userLifecycleEventTaskExecutor")
  public TaskExecutor userLifecycleEventTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.ExecutorProperties props = asyncProperties.getUserLifecycleEvent();
    executor.setCorePoolSize(props.getCorePoolSize());
    executor.setMaxPoolSize(props.getMaxPoolSize());
    executor.setQueueCapacity(props.getQueueCapacity());
    executor.setThreadNamePrefix("UserLifecycleEvent-Async-");

    executor.setRejectedExecutionHandler(
        (r, executorRef) -> {
          if (r instanceof UserLifecycleEventRunnable) {
            UserLifecycleEvent userLifecycleEvent = ((UserLifecycleEventRunnable) r).getEvent();
            logger.warn(
                "user lifecycle event rejected, queuing for retry: type={}, user={}, pool=[active={}, queue={}, completed={}]",
                userLifecycleEvent.lifecycleType().name(),
                userLifecycleEvent.user().sub(),
                executor.getActiveCount(),
                executor.getThreadPoolExecutor().getQueue().size(),
                executor.getThreadPoolExecutor().getCompletedTaskCount());
            userLifecycleEventRetryScheduler.enqueue(userLifecycleEvent);
          } else {
            logger.error(
                "unknown runnable rejected from user lifecycle event executor: {}",
                r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }

  @Bean("auditLogTaskExecutor")
  public TaskExecutor auditLogTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    AsyncProperties.ExecutorProperties props = asyncProperties.getAuditLog();
    executor.setCorePoolSize(props.getCorePoolSize());
    executor.setMaxPoolSize(props.getMaxPoolSize());
    executor.setQueueCapacity(props.getQueueCapacity());
    executor.setThreadNamePrefix("AuditLog-Async-");

    executor.setRejectedExecutionHandler(
        (r, executorRef) -> {
          if (r instanceof AuditLogRunnable) {
            AuditLog auditLog = ((AuditLogRunnable) r).getAuditLog();
            logger.warn(
                "audit log rejected, queuing for retry: id={}, type={}, pool=[active={}, queue={}, completed={}]",
                auditLog.id(),
                auditLog.type(),
                executor.getActiveCount(),
                executor.getThreadPoolExecutor().getQueue().size(),
                executor.getThreadPoolExecutor().getCompletedTaskCount());
            auditLogRetryScheduler.enqueue(auditLog);
          } else {
            logger.error(
                "unknown runnable rejected from audit log executor: {}", r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }
}
