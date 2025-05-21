package org.idp.server.core.authentication.webauthn;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.core.authentication.factory.AuthenticationDependencyContainer;

public class WebAuthnExecutorLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(WebAuthnExecutorLoader.class);

  public static WebAuthnExecutors load(AuthenticationDependencyContainer container) {
    Map<WebAuthnExecutorType, WebAuthnExecutor> executors = new HashMap<>();
    ServiceLoader<WebAuthnExecutorFactory> loader =
        ServiceLoader.load(WebAuthnExecutorFactory.class);

    for (WebAuthnExecutorFactory factory : loader) {
      WebAuthnExecutor webAuthnExecutor = factory.create(container);
      executors.put(webAuthnExecutor.type(), webAuthnExecutor);
      log.info(
          String.format("Dynamic Registered WebAuthnExecutor %s", webAuthnExecutor.type().value()));
    }

    return new WebAuthnExecutors(executors);
  }
}
