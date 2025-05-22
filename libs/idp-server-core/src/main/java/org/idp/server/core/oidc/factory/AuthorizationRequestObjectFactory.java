package org.idp.server.core.oidc.factory;

public interface AuthorizationRequestObjectFactory extends AuthorizationRequestFactory {

  RequestObjectFactoryType type();
}
