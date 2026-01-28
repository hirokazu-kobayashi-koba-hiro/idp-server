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

package org.idp.server.adapters.springboot.application.event;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retry scheduler for UserLifecycleEvents.
 *
 * <p>Periodically retries UserLifecycleEvents that were rejected due to ThreadPoolTaskExecutor
 * queue being full.
 *
 * <h2>Retry Strategy</h2>
 *
 * <ul>
 *   <li>Retry interval: 60 seconds
 *   <li>Maximum retry attempts: 3
 *   <li>On max retries exceeded: Log and discard
 * </ul>
 *
 * <h2>Graceful Shutdown</h2>
 *
 * <p>On application shutdown ({@code @PreDestroy}), attempts to flush remaining events in the retry
 * queue. However, since DB connections may be closed during shutdown, processing of all events is
 * not guaranteed.
 *
 * @see AsyncConfig
 */
@Component
public class UserLifecycleEventRetryScheduler {

  /** Maximum retry attempts. Events exceeding this count are discarded. */
  private static final int MAX_RETRIES = 3;

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventRetryScheduler.class);

  Queue<UserLifecycleEvent> retryQueue = new ConcurrentLinkedQueue<>();
  Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

  UserLifecycleEventApi userLifecycleEventApi;

  public UserLifecycleEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.userLifecycleEventApi = idpServerApplication.userLifecycleEventApi();
  }

  public void enqueue(UserLifecycleEvent userLifecycleEvent) {
    retryQueue.add(userLifecycleEvent);
    retryCountMap.putIfAbsent(createEventKey(userLifecycleEvent), 0);
  }

  @PreDestroy
  public void onShutdown() {
    if (!retryQueue.isEmpty()) {
      log.warn(
          "shutdown with {} user lifecycle events in retry queue, attempting flush",
          retryQueue.size());
      resendFailedEvents();
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    int queueSize = retryQueue.size();
    if (queueSize > 0) {
      log.info("processing user lifecycle event retry queue: {} events", queueSize);
    }

    while (!retryQueue.isEmpty()) {
      UserLifecycleEvent userLifecycleEvent = retryQueue.poll();
      String eventKey = createEventKey(userLifecycleEvent);

      try {
        int currentAttempt = retryCountMap.getOrDefault(eventKey, 0) + 1;
        log.info(
            "retry user lifecycle event (attempt {}/{}): type={}, user={}",
            currentAttempt,
            MAX_RETRIES,
            userLifecycleEvent.lifecycleType().name(),
            userLifecycleEvent.user().sub());
        userLifecycleEventApi.handle(userLifecycleEvent.tenantIdentifier(), userLifecycleEvent);
        retryCountMap.remove(eventKey);
      } catch (Exception e) {
        int count = retryCountMap.merge(eventKey, 1, Integer::sum);
        if (count < MAX_RETRIES) {
          log.warn(
              "retry user lifecycle event scheduled ({}/{}): type={}, user={}",
              count,
              MAX_RETRIES,
              userLifecycleEvent.lifecycleType().name(),
              userLifecycleEvent.user().sub());
          retryQueue.add(userLifecycleEvent);
        } else {
          log.error(
              "max retries exceeded, dropping user lifecycle event: type={}, user={}",
              userLifecycleEvent.lifecycleType().name(),
              userLifecycleEvent.user().sub(),
              e);
          retryCountMap.remove(eventKey);
        }
      }
    }
  }

  private String createEventKey(UserLifecycleEvent event) {
    return String.format(
        "%s:%s:%s",
        event.tenantIdentifier().value(), event.user().sub(), event.lifecycleType().name());
  }
}
