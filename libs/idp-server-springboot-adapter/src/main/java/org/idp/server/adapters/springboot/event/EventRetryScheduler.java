package org.idp.server.adapters.springboot.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventRetryScheduler {

  Logger log = LoggerFactory.getLogger(EventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();

  SecurityEventApi securityEventApi;

  public EventRetryScheduler(IdpServerApplication idpServerApplication) {
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
        securityEventApi.handle(securityEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", securityEvent.toMap());
        retryQueue.add(securityEvent);
      }
    }
  }
}
