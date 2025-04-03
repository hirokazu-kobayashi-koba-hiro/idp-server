package org.idp.server.adapters.springboot.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.SecurityEventApi;
import org.idp.server.core.sharedsignal.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EventRetryScheduler {

  Logger log = LoggerFactory.getLogger(EventRetryScheduler.class);

  Queue<Event> retryQueue = new ConcurrentLinkedQueue<>();

  SecurityEventApi securityEventApi;

  public EventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.securityEventApi = idpServerApplication.eventFunction();
  }

  public void enqueue(Event event) {
    retryQueue.add(event);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      Event event = retryQueue.poll();
      try {
        log.info("retry event: {}", event.toMap());
        securityEventApi.handle(event);
      } catch (Exception e) {
        log.error("retry event error: {}", event.toMap());
        retryQueue.add(event);
      }
    }
  }
}
