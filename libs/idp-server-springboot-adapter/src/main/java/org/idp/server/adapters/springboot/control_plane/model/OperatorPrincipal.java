package org.idp.server.adapters.springboot.control_plane.model;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.identity.User;
import org.idp.server.core.token.OAuthToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class OperatorPrincipal extends AbstractAuthenticationToken {

  User user;
  OAuthToken oauthToken;

  public OperatorPrincipal(
      User user, OAuthToken oAuthToken, List<IdpControlPlaneScope> idpControlPlaneScopes) {
    super(idpControlPlaneScopes);
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

  public OAuthToken getOAuthToken() {
    return oauthToken;
  }

  public RequestedClientId getRequestedClientId() {
    return oauthToken.requestedClientId();
  }
}
