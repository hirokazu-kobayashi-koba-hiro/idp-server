/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.plugin.request;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.idp.server.core.oidc.factory.AuthorizationRequestObjectFactory;
import org.idp.server.core.oidc.factory.RequestObjectFactoryType;
import org.idp.server.platform.log.LoggerWrapper;

public class AuthorizationRequestFactoryPluginLoader {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AuthorizationRequestFactoryPluginLoader.class);

  public static Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> load() {
    Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> factories = new HashMap<>();

    ServiceLoader<AuthorizationRequestObjectFactory> serviceLoaders =
        ServiceLoader.load(AuthorizationRequestObjectFactory.class);
    for (AuthorizationRequestObjectFactory requestObjectFactory : serviceLoaders) {
      factories.put(requestObjectFactory.type(), requestObjectFactory);
      log.info(
          "Dynamic Registered AuthorizationRequestObjectFactory "
              + requestObjectFactory.getClass().getSimpleName());
    }

    return factories;
  }
}
