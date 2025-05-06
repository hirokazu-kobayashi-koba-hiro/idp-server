package org.idp.server.adapters.springboot.event;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventApi;
import org.idp.server.IdpServerApplication;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class UserLifecycleEventListerService {

  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventListerService.class);
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
    log.info("onEvent: {}", userLifecycleEvent.lifecycleType().name());

    taskExecutor.execute(
        new UserLifecycleEventRunnable(
            userLifecycleEvent,
            event -> {
              userLifecycleEventApi.handle(event.tenantIdentifier(), event);
            }));
  }
}
