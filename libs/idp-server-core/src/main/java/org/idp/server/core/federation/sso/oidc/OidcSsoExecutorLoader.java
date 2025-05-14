package org.idp.server.core.federation.sso.oidc;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.federation.sso.SsoProvider;

public class OidcSsoExecutorLoader {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(OidcSsoExecutorLoader.class);

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
