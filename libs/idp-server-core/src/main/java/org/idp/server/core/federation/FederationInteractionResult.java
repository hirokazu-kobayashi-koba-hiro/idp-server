package org.idp.server.core.federation;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.federation.oidc.OidcSsoSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class FederationInteractionResult {

  FederationInteractionStatus status;
  User user;
  Authentication authentication;
  Map<String, Object> response;
  DefaultSecurityEventType eventType;

  private FederationInteractionResult(
      FederationInteractionStatus status,
      User user,
      Authentication authentication,
      Map<String, Object> response,
      DefaultSecurityEventType eventType) {
    this.status = status;
    this.user = user;
    this.authentication = authentication;
    this.response = response;
    this.eventType = eventType;
  }

  public static FederationInteractionResult success(OidcSsoSession session, User user) {
    FederationInteractionStatus status = FederationInteractionStatus.SUCCESS;
    Authentication authentication = new Authentication();
    Map<String, Object> response =
        Map.of("id", session.authorizationRequestId(), "tenant_id", session.tenantId());

    DefaultSecurityEventType eventType = DefaultSecurityEventType.federation_success;
    return new FederationInteractionResult(status, user, authentication, response, eventType);
  }

  public FederationInteractionStatus status() {
    return status;
  }

  public boolean isSuccess() {
    return status.isSuccess();
  }

  public boolean isError() {
    return status.isError();
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public Map<String, Object> response() {
    return response;
  }

  public DefaultSecurityEventType eventType() {
    return eventType;
  }

  public boolean hasUser() {
    return Objects.nonNull(user) && user.exists();
  }

  public boolean hasAuthentication() {
    return Objects.nonNull(authentication) && authentication.exists();
  }

  public AuthorizationRequestIdentifier authorizationRequestIdentifier() {
    return null;
  }
}
