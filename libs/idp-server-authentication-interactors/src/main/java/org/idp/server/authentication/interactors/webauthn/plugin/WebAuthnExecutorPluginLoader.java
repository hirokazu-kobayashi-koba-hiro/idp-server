package org.idp.server.authentication.interactors.webauthn.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutor;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorFactory;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutorType;
import org.idp.server.authentication.interactors.webauthn.WebAuthnExecutors;
import org.idp.server.core.oidc.authentication.plugin.AuthenticationDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;

public class WebAuthnExecutorPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(WebAuthnExecutorPluginLoader.class);

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
