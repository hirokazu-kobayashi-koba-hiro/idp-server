package org.idp.server.core.authentication.sms;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public class SmsAuthenticationExecutorLoader {

  private static final Logger log =
      Logger.getLogger(SmsAuthenticationExecutorLoader.class.getName());

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
