package org.idp.server.adapters.springboot.application.event;

import org.idp.server.IdpServerApplication;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.security.SecurityEvent;
import org.idp.server.core.security.SecurityEventApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventListerService {

  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventListerService.class);
  TaskExecutor taskExecutor;
  SecurityEventApi securityEventApi;

  public SecurityEventListerService(
      @Qualifier("securityEventTaskExecutor") TaskExecutor taskExecutor,
      IdpServerApplication idpServerApplication) {
    this.taskExecutor = taskExecutor;
    this.securityEventApi = idpServerApplication.securityEventApi();
  }

  @Async
  @EventListener
  public void onEvent(SecurityEvent securityEvent) {
    log.info("onEvent: {}", securityEvent.toMap());

    taskExecutor.execute(
        new SecurityEventRunnable(
            securityEvent,
            event -> {
              securityEventApi.handle(event.tenantIdentifier(), event);
            }));
  }
}
