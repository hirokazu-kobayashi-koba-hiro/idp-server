package org.idp.server.authentication.interactors.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class SmsAuthenticationExecutorLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(SmsAuthenticationExecutorLoader.class);

  public static SmsAuthenticationExecutors load(AuthenticationDependencyContainer container) {
    Map<SmsAuthenticationType, SmsAuthenticationExecutor> executors = new HashMap<>();
    ServiceLoader<SmsAuthenticationExecutorFactory> loader =
        ServiceLoader.load(SmsAuthenticationExecutorFactory.class);

    for (SmsAuthenticationExecutorFactory factory : loader) {
      SmsAuthenticationExecutor executor = factory.create(container);
      executors.put(executor.type(), executor);
      log.info("Dynamic Registered SmsAuthenticationExecutor " + executor.type().name());
    }

    return new SmsAuthenticationExecutors(executors);
  }
}
