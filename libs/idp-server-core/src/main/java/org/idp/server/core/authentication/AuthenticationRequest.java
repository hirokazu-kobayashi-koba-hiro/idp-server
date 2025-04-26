package org.idp.server.core.authentication;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.identity.User;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.AuthorizationFlow;
import org.idp.server.core.type.oauth.RequestedClientId;

public class AuthenticationRequest {

  AuthorizationFlow authorizationFlow;
  TenantIdentifier tenantIdentifier;
  RequestedClientId requestedClientId;
  User user;
  List<String> availableAuthenticationTypes;
  List<String> requiredAnyOfAuthenticationTypes;
  LocalDateTime createdAt;
  LocalDateTime expiredAt;

  public AuthenticationRequest() {}

  public AuthenticationRequest(
      AuthorizationFlow authorizationFlow,
      TenantIdentifier tenantIdentifier,
      RequestedClientId requestedClientId,
      User user,
      List<String> availableAuthenticationTypes,
      List<String> requiredAnyOfAuthenticationTypes,
      LocalDateTime createdAt,
      LocalDateTime expiredAt) {
    this.authorizationFlow = authorizationFlow;
    this.tenantIdentifier = tenantIdentifier;
    this.requestedClientId = requestedClientId;
    this.user = user;
    this.availableAuthenticationTypes = availableAuthenticationTypes;
    this.requiredAnyOfAuthenticationTypes = requiredAnyOfAuthenticationTypes;
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

  public List<String> availableAuthenticationTypes() {
    return availableAuthenticationTypes;
  }

  public List<String> requiredAnyOfAuthenticationTypes() {
    return requiredAnyOfAuthenticationTypes;
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
    map.put("available_authentication_types", availableAuthenticationTypes);
    map.put("required_any_of_authentication_types", requiredAnyOfAuthenticationTypes);
    map.put("created_at", createdAt.toString());
    map.put("expired_at", expiredAt.toString());
    return map;
  }

  public AuthenticationRequest updateUser(
      AuthenticationInteractionRequestResult interactionRequestResult) {
    User user = interactionRequestResult.user();
    return new AuthenticationRequest(
        authorizationFlow,
        tenantIdentifier,
        requestedClientId,
        user,
        availableAuthenticationTypes,
        requiredAnyOfAuthenticationTypes,
        createdAt,
        expiredAt);
  }
}
