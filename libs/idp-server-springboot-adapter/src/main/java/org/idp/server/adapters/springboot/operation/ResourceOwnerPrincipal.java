package org.idp.server.adapters.springboot.operation;

import java.util.List;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.type.oauth.RequestedClientId;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class ResourceOwnerPrincipal extends AbstractAuthenticationToken {

  User user;
  OAuthToken oAuthToken;

  public ResourceOwnerPrincipal(User user, OAuthToken oAuthToken, List<IdPScope> idPScopes) {
    super(idPScopes);
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
