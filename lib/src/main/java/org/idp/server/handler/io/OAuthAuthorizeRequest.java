package org.idp.server.handler.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.oauth.TokenIssuer;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  String id;
  String tokenIssuer;
  User user;
  Map<String, Object> customProperties = new HashMap<>();

  public OAuthAuthorizeRequest(String id, String tokenIssuer, User user) {
    this.id = id;
    this.tokenIssuer = tokenIssuer;
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
    return new TokenIssuer(tokenIssuer);
  }
}