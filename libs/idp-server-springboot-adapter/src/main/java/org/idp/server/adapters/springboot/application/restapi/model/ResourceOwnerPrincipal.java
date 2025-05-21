package org.idp.server.adapters.springboot.application.restapi.model;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class ResourceOwnerPrincipal extends AbstractAuthenticationToken {

  User user;
  OAuthToken oAuthToken;

  public ResourceOwnerPrincipal(
      User user, OAuthToken oAuthToken, List<IdPApplicationScope> idPApplicationScopes) {
    super(idPApplicationScopes);
    this.user = user;
    this.oAuthToken = oAuthToken;
  }

  @Override
  public Object getCredentials() {
    return oAuthToken;
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  public User getUser() {
    return user;
  }

  public OAuthToken getOAuthToken() {
    return oAuthToken;
  }

  public RequestedClientId getRequestedClientId() {
    return oAuthToken.requestedClientId();
  }
}
