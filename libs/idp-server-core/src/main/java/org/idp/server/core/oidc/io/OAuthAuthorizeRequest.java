package org.idp.server.core.oidc.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.basic.type.extension.CustomProperties;

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

  public TenantIdentifier tenantIdentifier() {
    return tenant.identifier();
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
