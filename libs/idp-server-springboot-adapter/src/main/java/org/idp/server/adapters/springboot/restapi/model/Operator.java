package org.idp.server.adapters.springboot.restapi.model;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class Operator extends AbstractAuthenticationToken {

  User user;
  OAuthToken oauthToken;

  public Operator(User user, OAuthToken oAuthToken, List<IdPScope> idPScopes) {
    super(idPScopes);
    this.user = user;
    this.oauthToken = oAuthToken;
  }

  @Override
  public Object getCredentials() {
    return oauthToken;
  }

  @Override
  public Object getPrincipal() {
    return this;
  }

  public User getUser() {
    return user;
  }

  public RequestedClientId getRequestedClientId() {
    return oauthToken.requestedClientId();
  }
}
