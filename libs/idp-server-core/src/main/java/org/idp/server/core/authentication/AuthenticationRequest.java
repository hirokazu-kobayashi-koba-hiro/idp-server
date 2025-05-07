package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.AuthorizationFlow;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.basic.type.oidc.AcrValues;
import org.idp.server.core.identity.User;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;

public class AuthenticationRequest {

  AuthorizationFlow authorizationFlow;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  User user;
  AuthenticationContext context;
  LocalDateTime createdAt;
  LocalDateTime expiredAt;

  public AuthenticationRequest() {}

  public AuthenticationRequest(
      AuthorizationFlow authorizationFlow,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      User user,
      AuthenticationContext context,
      LocalDateTime createdAt,
      LocalDateTime expiredAt) {
    this.authorizationFlow = authorizationFlow;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.user = user;
    this.context = context;
    this.createdAt = createdAt;
    this.expiredAt = expiredAt;
  }

  public AuthorizationFlow authorizationFlow() {
    return authorizationFlow;
  }

  public TenantIdentifier tenantIdentifier() {
    return tenantIdentifier;
  }

  public RequestedClientId requestedClientId() {
    return requestedClientId;
  }

  public User user() {
    return user;
  }

  public AuthenticationContext context() {
    return context;
  }

  public LocalDateTime createdAt() {
    return createdAt;
  }

  public LocalDateTime expiredAt() {
    return expiredAt;
  }

  public boolean hasUser() {
    return user != null && user.exists();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("authorization_flow", authorizationFlow.value());
    map.put("tenant_id", tenantIdentifier.value());
    map.put("client_id", requestedClientId.value());
    map.put("user", user.toMap());
    map.put("context", context.toMap());
    map.put("created_at", createdAt.toString());
    map.put("expired_at", expiredAt.toString());
    return map;
  }

  public AuthenticationRequest updateWithUser(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    User user = interactionRequestResult.user();
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        context,
        createdAt,
        expiredAt);
  }

  public AcrValues acrValues() {
    return context.acrValues();
  }

  public Scopes scopes() {
    return context.scopes();
  }
}
