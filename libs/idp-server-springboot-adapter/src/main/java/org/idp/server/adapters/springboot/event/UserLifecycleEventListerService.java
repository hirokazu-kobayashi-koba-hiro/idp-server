package org.idp.server.adapters.springboot.event;

import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleEventApi;
import org.idp.server.usecases.IdpServerApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserLifecycleEventListerService {

  Logger log = LoggerFactory.getLogger(UserLifecycleEventListerService.class);
  TaskExecutor taskExecutor;
  UserLifecycleEventApi userLifecycleEventApi;

  public UserLifecycleEventListerService(
      @Qualifier("userLifecycleEventTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.userLifecycleEventApi = idpServerApplication.userLifecycleEventApi();
  }

  @Async
  @EventListener
  public void onEvent(UserLifecycleEvent userLifecycleEvent) {
    log.info("onEvent: {}", userLifecycleEvent.lifecycleOperation().name());

    taskExecutor.execute(
        new UserLifecycleEventRunnable(
            userLifecycleEvent,
            event -> {
              userLifecycleEventApi.handle(event.tenantIdentifier(), event);
            }));
  }
}
