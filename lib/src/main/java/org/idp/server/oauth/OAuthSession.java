package org.idp.server.oauth;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.oauth.authentication.Authentication;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;

public class OAuthSession implements Serializable {
  OAuthSessionKey oAuthSessionKey;
  User user;
  Authentication authentication;
  Map<String, Object> customProperties = new HashMap<>();

  public OAuthSession(OAuthSessionKey oAuthSessionKey, User user, Authentication authentication) {
    this.oAuthSessionKey = oAuthSessionKey;
    this.user = user;
    this.authentication = authentication;
  }

  public OAuthSessionKey oAuthSessionKey() {
    return oAuthSessionKey;
  }

  public String sessionKeyValue() {
    return oAuthSessionKey.key();
  }

  public User user() {
    return user;
  }

  public Authentication authentication() {
    return authentication;
  }

  public LocalDateTime authenticationTime() {
    return authentication.time();
  }

  public List<String> authenticationMethods() {
    return authentication.methods();
  }

  public boolean isValid(AuthorizationRequest request) {
    if (request.isPromptLogin()) {
      return false;
    }
    LocalDateTime authenticationTime = authentication.time();
    LocalDateTime now = SystemDateTime.now();
    if (now.isAfter(authenticationTime.plusSeconds(request.maxAge().toLongValue()))) {
      return false;
    }
    // TODO logic
    return true;
  }

  public Map<String, Object> customProperties() {
    return customProperties;
  }
}
