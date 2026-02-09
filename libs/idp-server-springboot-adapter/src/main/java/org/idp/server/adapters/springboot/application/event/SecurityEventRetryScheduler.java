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
import java.util.concurrent.atomic.AtomicInteger;
import org.idp.server.adapters.springboot.AsyncProperties;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Retry scheduler for SecurityEvents.
 *
 * <p>Periodically retries SecurityEvents that were rejected due to ThreadPoolTaskExecutor queue
 * being full.
 *
 * <h2>Retry Strategy</h2>
 *
 * <ul>
 *   <li>Retry interval: 60 seconds
 *   <li>Maximum retry attempts: 3
 *   <li>Retry queue capacity: configurable (default 1000), prevents unbounded memory growth
 *   <li>On max retries or queue full: Log and discard
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
public class SecurityEventRetryScheduler {

  /** Maximum retry attempts. Events exceeding this count are discarded. */
  private static final int MAX_RETRIES = 3;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();
  Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();
  AtomicInteger queueSize = new AtomicInteger(0);

  SecurityEventApi securityEventApi;
  int maxRetryQueueSize;

  public SecurityEventRetryScheduler(
      IdpServerApplication idpServerApplication, AsyncProperties asyncProperties) {
    this.securityEventApi = idpServerApplication.securityEventApi();
    this.maxRetryQueueSize = asyncProperties.getSecurityEvent().getRetryQueueCapacity();
  }

  public void enqueue(SecurityEvent securityEvent) {
    if (queueSize.get() >= maxRetryQueueSize) {
      log.error(
          "retry queue full ({}/{}), dropping security event: id={}, type={}, tenant={}",
          queueSize.get(),
          maxRetryQueueSize,
          securityEvent.identifier().value(),
          securityEvent.type().value(),
          securityEvent.tenantIdentifierValue());
      return;
    }
    retryQueue.add(securityEvent);
    queueSize.incrementAndGet();
    retryCountMap.putIfAbsent(securityEvent.identifier().value(), 0);
  }

  @PreDestroy
  public void onShutdown() {
    if (!retryQueue.isEmpty()) {
      log.warn(
          "shutdown with {} security events in retry queue, attempting flush", retryQueue.size());
      resendFailedEvents();
    }
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    int currentSize = retryQueue.size();
    if (currentSize == 0) {
      log.debug("security event retry queue is empty, skipping");
      return;
    }

    log.info("processing security event retry queue: {} events", currentSize);

    int successCount = 0;
    int skippedCount = 0;
    int requeuedCount = 0;

    while (!retryQueue.isEmpty()) {
      SecurityEvent securityEvent = retryQueue.poll();
      queueSize.decrementAndGet();
      String eventId = securityEvent.identifier().value();
      String eventType = securityEvent.type().value();
      String tenantId = securityEvent.tenantIdentifierValue();

      try {
        int currentAttempt = retryCountMap.getOrDefault(eventId, 0) + 1;
        log.debug(
            "retry security event (attempt {}/{}): id={}, type={}, tenant={}",
            currentAttempt,
            MAX_RETRIES,
            eventId,
            eventType,
            tenantId);
        securityEventApi.handle(securityEvent.tenantIdentifier(), securityEvent);
        retryCountMap.remove(eventId);
        successCount++;
      } catch (Exception e) {
        int count = retryCountMap.merge(eventId, 1, Integer::sum);
        if (count < MAX_RETRIES) {
          log.warn(
              "retry security event scheduled ({}/{}): id={}, type={}, tenant={}",
              count,
              MAX_RETRIES,
              eventId,
              eventType,
              tenantId);
          retryQueue.add(securityEvent);
          queueSize.incrementAndGet();
          requeuedCount++;
        } else {
          log.error(
              "max retries exceeded, dropping security event: id={}, type={}, tenant={}, error={}",
              eventId,
              eventType,
              tenantId,
              e.getMessage());
          retryCountMap.remove(eventId);
          skippedCount++;
        }
      }
    }

    log.info(
        "security event retry completed: success={}, skipped={}, requeued={}",
        successCount,
        skippedCount,
        requeuedCount);
  }
}
