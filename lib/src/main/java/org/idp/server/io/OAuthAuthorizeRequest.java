package org.idp.server.io;

import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.type.CustomProperties;
import org.idp.server.core.type.TokenIssuer;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  String id;
  String tokenIssure;
  User user;
  Map<String, Object> customProperties;
  // TODO user

  public OAuthAuthorizeRequest(String id, String tokenIssuer, User user) {
    this.id = id;
    this.tokenIssure = tokenIssuer;
    this.user = user;
  }

  public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public User user() {
    return user;
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }

  public TokenIssuer toTokenIssuer() {
    return new TokenIssuer(tokenIssure);
  }
}
