package org.idp.server.core.mfa;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.security.event.DefaultSecurityEventType;

public class MfaInteractionResult {

  MfaInteractionType type;
  User user;
  Authentication authentication;
  Map<String, Object> response;
  DefaultSecurityEventType eventType;

  public MfaInteractionResult(
      MfaInteractionType type, Map<String, Object> response, DefaultSecurityEventType eventType) {
    this.type = type;
    this.response = response;
    this.eventType = eventType;
  }

  public MfaInteractionResult(
      MfaInteractionType type,
      User user,
      Authentication authentication,
      Map<String, Object> response,
      DefaultSecurityEventType eventType) {
    this.type = type;
    this.user = user;
    this.authentication = authentication;
    this.response = response;
    this.eventType = eventType;
  }

  public MfaInteractionType type() {
    return type;
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
}
