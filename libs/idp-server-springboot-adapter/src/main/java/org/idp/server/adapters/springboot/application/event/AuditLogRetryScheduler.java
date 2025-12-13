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
import org.idp.server.platform.audit.AuditLog;
import org.idp.server.platform.audit.AuditLogApi;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuditLogRetryScheduler {

  private static final int MAX_RETRIES = 3;

  LoggerWrapper log = LoggerWrapper.getLogger(AuditLogRetryScheduler.class);

  Queue<AuditLog> retryQueue = new ConcurrentLinkedQueue<>();
  Map<String, Integer> retryCountMap = new ConcurrentHashMap<>();

  AuditLogApi auditLogApi;

  public AuditLogRetryScheduler(IdpServerApplication idpServerApplication) {
    this.auditLogApi = idpServerApplication.auditLogApi();
  }

  public void enqueue(AuditLog auditLog) {
    retryQueue.add(auditLog);
    retryCountMap.putIfAbsent(auditLog.id(), 0);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    int queueSize = retryQueue.size();
    if (queueSize > 0) {
      log.info("processing audit log retry queue: {} events", queueSize);
    }

    while (!retryQueue.isEmpty()) {
      AuditLog auditLog = retryQueue.poll();
      String auditLogId = auditLog.id();

      try {
        int currentAttempt = retryCountMap.getOrDefault(auditLogId, 0) + 1;
        log.info(
            "retry audit log (attempt {}/{}): id={}, type={}",
            currentAttempt,
            MAX_RETRIES,
            auditLogId,
            auditLog.type());
        auditLogApi.handle(auditLog.tenantIdentifier(), auditLog);
        retryCountMap.remove(auditLogId);
      } catch (Exception e) {
        int count = retryCountMap.merge(auditLogId, 1, Integer::sum);
        if (count < MAX_RETRIES) {
          log.warn(
              "retry audit log scheduled ({}/{}): id={}, type={}",
              count,
              MAX_RETRIES,
              auditLogId,
              auditLog.type());
          retryQueue.add(auditLog);
        } else {
          log.error(
              "max retries exceeded, dropping audit log: id={}, type={}",
              auditLogId,
              auditLog.type(),
              e);
          retryCountMap.remove(auditLogId);
        }
      }
    }
  }
}
