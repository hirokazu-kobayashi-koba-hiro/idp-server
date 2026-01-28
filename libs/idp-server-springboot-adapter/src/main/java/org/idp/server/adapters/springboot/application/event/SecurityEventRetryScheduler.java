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

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.security.SecurityEvent;
import org.idp.server.platform.security.SecurityEventApi;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventRetryScheduler {

  private static final int MAX_RETRIES = 3;

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();
  Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

  SecurityEventApi securityEventApi;

  public SecurityEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.securityEventApi = idpServerApplication.securityEventApi();
  }

  public void enqueue(SecurityEvent securityEvent) {
    retryQueue.add(securityEvent);
    retryCountMap.putIfAbsent(securityEvent.identifier().value(), 0);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    int queueSize = retryQueue.size();
    if (queueSize == 0) {
      log.debug("security event retry queue is empty, skipping");
      return;
    }

    log.info("processing security event retry queue: {} events", queueSize);

    int successCount = 0;
    int skippedCount = 0;
    int requeuedCount = 0;

    while (!retryQueue.isEmpty()) {
      SecurityEvent securityEvent = retryQueue.poll();
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
