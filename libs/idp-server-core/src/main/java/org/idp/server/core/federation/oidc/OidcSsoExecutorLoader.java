package org.idp.server.core.federation.oidc;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import org.idp.server.core.federation.SsoProvider;

public class OidcSsoExecutorLoader {

  private static final Logger log = Logger.getLogger(OidcSsoExecutorLoader.class.getName());

  public static OidcSsoExecutors load() {
    Map<SsoProvider, OidcSsoExecutor> executors = new HashMap<>();
    ServiceLoader<OidcSsoExecutor> ssoExecutorServiceLoaders =
        ServiceLoader.load(OidcSsoExecutor.class);

    for (OidcSsoExecutor executor : ssoExecutorServiceLoaders) {
      SsoProvider provider = executor.type();
      executors.put(provider, executor);
      log.info("Dynamic Registered SSO provider " + provider.name());
    }

    return new OidcSsoExecutors(executors);
  }
}
