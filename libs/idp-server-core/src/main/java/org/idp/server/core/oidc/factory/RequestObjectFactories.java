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
