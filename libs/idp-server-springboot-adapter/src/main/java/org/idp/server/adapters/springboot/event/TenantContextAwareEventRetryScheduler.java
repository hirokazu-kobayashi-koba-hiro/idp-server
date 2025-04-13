package org.idp.server.adapters.springboot.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TenantContextAwareEventRetryScheduler {

  Logger log = LoggerFactory.getLogger(TenantContextAwareEventRetryScheduler.class);

  Queue<SecurityEvent> retryQueue = new ConcurrentLinkedQueue<>();

  SecurityEventApi securityEventApi;

  public TenantContextAwareEventRetryScheduler(IdpServerApplication idpServerApplication) {
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
        TenantContext.set(securityEvent.tenantIdentifier());
        log.info("retry event: {}", securityEvent.toMap());
        securityEventApi.handle(securityEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", securityEvent.toMap());
        retryQueue.add(securityEvent);
      } finally {
        TenantContext.clear();
      }
    }
  }
}
