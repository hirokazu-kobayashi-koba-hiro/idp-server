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


package org.idp.server.adapters.springboot.application.config;

import org.idp.server.adapters.springboot.application.event.SecurityEventRetryScheduler;
import org.idp.server.adapters.springboot.application.event.SecurityEventRunnable;
import org.idp.server.adapters.springboot.application.event.UserLifecycleEventRetryScheduler;
import org.idp.server.adapters.springboot.application.event.UserLifecycleEventRunnable;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfig {

  LoggerWrapper logger = LoggerWrapper.getLogger(AsyncConfig.class);
  SecurityEventRetryScheduler securityEventRetryScheduler;
  UserLifecycleEventRetryScheduler userLifecycleEventRetryScheduler;

  public AsyncConfig(
      SecurityEventRetryScheduler securityEventRetryScheduler,
      UserLifecycleEventRetryScheduler userLifecycleEventRetryScheduler) {
    this.securityEventRetryScheduler = securityEventRetryScheduler;
    this.userLifecycleEventRetryScheduler = userLifecycleEventRetryScheduler;
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
            securityEventRetryScheduler.enqueue(securityEvent);
          } else {

            logger.error("unknown EventRunnable" + r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }

  @Bean("userLifecycleEventTaskExecutor")
  public TaskExecutor userLifecycleEventTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(5);
    executor.setMaxPoolSize(10);
    executor.setQueueCapacity(50);
    executor.setThreadNamePrefix("UserLifecycleEvent-Async-");

    executor.setRejectedExecutionHandler(
        (r, executor1) -> {
          logger.warn("Rejected Execution Handler");

          if (r instanceof UserLifecycleEventRunnable) {
            UserLifecycleEvent userLifecycleEvent = ((UserLifecycleEventRunnable) r).getEvent();
            userLifecycleEventRetryScheduler.enqueue(userLifecycleEvent);
          } else {

            logger.error("unknown EventRunnable" + r.getClass().getName());
          }
        });

    executor.initialize();
    return executor;
  }
}
