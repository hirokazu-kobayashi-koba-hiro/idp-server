/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.factory;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.plugin.request.AuthorizationRequestFactoryPluginLoader;
import org.idp.server.platform.exception.UnSupportedException;

public class RequestObjectFactories {

  Map<RequestObjectFactoryType, AuthorizationRequestFactory> factories;

  public RequestObjectFactories() {
    this.factories = new HashMap<>();
    factories.put(RequestObjectFactoryType.DEFAULT, new RequestObjectPatternFactory());
    Map<RequestObjectFactoryType, AuthorizationRequestObjectFactory> loaded =
        AuthorizationRequestFactoryPluginLoader.load();
    factories.putAll(loaded);
  }

  public AuthorizationRequestFactory get(RequestObjectFactoryType type) {
    AuthorizationRequestFactory factory = factories.get(type);

    if (factory == null) {
      throw new UnSupportedException("Unknown request object factory type " + type.name());
    }

    return factory;
  }
}
