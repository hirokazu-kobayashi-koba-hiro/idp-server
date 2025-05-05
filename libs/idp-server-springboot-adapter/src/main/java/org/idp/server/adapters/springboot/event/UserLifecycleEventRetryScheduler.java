package org.idp.server.adapters.springboot.event;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleEventApi;
import org.idp.server.usecases.IdpServerApplication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserLifecycleEventRetryScheduler {

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventRetryScheduler.class);

  Queue<UserLifecycleEvent> retryQueue = new ConcurrentLinkedQueue<>();

  UserLifecycleEventApi userLifecycleEventApi;

  public UserLifecycleEventRetryScheduler(IdpServerApplication idpServerApplication) {
    this.userLifecycleEventApi = idpServerApplication.userLifecycleEventApi();
  }

  public void enqueue(UserLifecycleEvent userLifecycleEvent) {
    retryQueue.add(userLifecycleEvent);
  }

  @Scheduled(fixedDelay = 60_000)
  public void resendFailedEvents() {
    while (!retryQueue.isEmpty()) {
      UserLifecycleEvent userLifecycleEvent = retryQueue.poll();
      try {
        log.info("retry event: {}", userLifecycleEvent.lifecycleOperation().name());
        userLifecycleEventApi.handle(userLifecycleEvent.tenantIdentifier(), userLifecycleEvent);
      } catch (Exception e) {
        log.error("retry event error: {}", userLifecycleEvent.lifecycleOperation().name());
        retryQueue.add(userLifecycleEvent);
      }
    }
  }
}
