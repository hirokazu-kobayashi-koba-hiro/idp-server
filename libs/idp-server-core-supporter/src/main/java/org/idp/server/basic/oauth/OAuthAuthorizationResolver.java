package org.idp.server.basic.oauth;

public interface OAuthAuthorizationResolver {

  String resolve(OAuthAuthorizationConfiguration oAuthAuthorizationConfig);
}
