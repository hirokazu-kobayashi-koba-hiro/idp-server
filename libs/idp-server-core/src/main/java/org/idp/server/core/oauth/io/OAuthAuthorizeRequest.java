package org.idp.server.core.oauth.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.extension.CustomProperties;

/** OAuthAuthorizeRequest */
public class OAuthAuthorizeRequest {
  Tenant tenant;
  String id;
  User user;
  Authentication authentication;
  Map<String, Object> customProperties = new HashMap<>();

  public OAuthAuthorizeRequest(Tenant tenant, String id, User user, Authentication authentication) {
    this.tenant = tenant;
    this.id = id;
    this.user = user;
    this.authentication = authentication;
  }

  public OAuthAuthorizeRequest setCustomProperties(Map<String, Object> customProperties) {
    this.customProperties = customProperties;
    return this;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AuthorizationRequestIdentifier toIdentifier() {
    return new AuthorizationRequestIdentifier(id);
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public CustomProperties toCustomProperties() {
    return new CustomProperties(customProperties);
  }
}
