package org.idp.server.adapters.springboot.event;

import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.api.SecurityEventApi;
import org.idp.server.core.security.SecurityEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventListerService {

  Logger log = LoggerFactory.getLogger(SecurityEventListerService.class);
  TaskExecutor taskExecutor;
  SecurityEventApi securityEventApi;

  public SecurityEventListerService(
      @Qualifier("securityEventTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.securityEventApi = idpServerApplication.eventFunction();
  }

  @Async
  @EventListener
  public void onEvent(SecurityEvent securityEvent) {
    log.info("onEvent: {}", securityEvent.toMap());
    securityEventApi.handle(securityEvent);
    taskExecutor.execute(
        new EventRunnable(
            securityEvent,
            e -> {
              securityEventApi.handle(e);
            }));
  }
}
