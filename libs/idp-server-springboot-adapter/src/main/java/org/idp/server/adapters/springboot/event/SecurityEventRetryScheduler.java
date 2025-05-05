package org.idp.server.adapters.springboot.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SecurityEventRetryScheduler {

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();

  SecurityEventApi securityEventApi;

  public SecurityEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.securityEventApi = idpServerApplication.securityEventApi();
  }

  public void enqueue(SecurityEvent securityEvent) {
    retryQueue.add(securityEvent);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      SecurityEvent securityEvent = retryQueue.poll();
      try {
        log.info("retry event: {}", securityEvent.toMap());
        securityEventApi.handle(securityEvent.tenantIdentifier(), securityEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", securityEvent.toMap());
        retryQueue.add(securityEvent);
      }
    }
  }
}
