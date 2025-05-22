/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.federation.sso.oidc;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.federation.sso.SsoProvider;
import org.idp.server.platform.log.LoggerWrapper;

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
