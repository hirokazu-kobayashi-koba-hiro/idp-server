package org.idp.server.core.oauth;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.request.AuthorizationRequest;

public class OAuthSession implements Serializable {
  OAuthSessionKey oAuthSessionKey;
  User user;
  Authentication authentication;
  LocalDateTime expiredAt;
  HashMap<String, Object> attributes = new HashMap<>();

  public OAuthSession() {}

  public OAuthSession(
      OAuthSessionKey oAuthSessionKey,
      User user,
      Authentication authentication,
      LocalDateTime expiredAt) {
    this.oAuthSessionKey = oAuthSessionKey;
    this.user = user;
    this.authentication = authentication;
    this.expiredAt = expiredAt;
  }

  public OAuthSession(
      OAuthSessionKey oAuthSessionKey,
      User user,
      Authentication authentication,
      LocalDateTime expiredAt,
      HashMap<String, Object> attributes) {
    this.oAuthSessionKey = oAuthSessionKey;
    this.user = user;
    this.authentication = authentication;
    this.expiredAt = expiredAt;
    this.attributes = attributes;
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

    if (authentication == null || !authentication.hasAuthenticationTime()) {
      return false;
    }

    LocalDateTime now = SystemDateTime.now();
    if (isExpire(now)) {
      return false;
    }

    LocalDateTime authenticationTime = authentication.time();
    if (now.isAfter(authenticationTime.plusSeconds(request.maxAge().toLongValue()))) {
      return false;
    }
    // TODO logic
    return true;
  }

  public boolean isExpire(LocalDateTime now) {
    return expiredAt.isBefore(now);
  }

  public Map<String, Object> customProperties() {
    return user.customPropertiesValue();
  }

  public OAuthSession didAuthenticationPassword(OAuthSessionKey oAuthSessionKey, User user) {

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("pwd")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new OAuthSession(
        oAuthSessionKey, user, authentication, SystemDateTime.now().plusSeconds(3600));
  }

  public OAuthSession didEmailAuthentication(OAuthSessionKey oAuthSessionKey) {

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("otp")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    user.setEmailVerified(true);

    return new OAuthSession(
        oAuthSessionKey, user, authentication, SystemDateTime.now().plusSeconds(3600));
  }

  public OAuthSession didWebAuthnAuthentication(OAuthSessionKey oAuthSessionKey, User user) {

    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(new ArrayList<>(List.of("hwk")))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));

    return new OAuthSession(
        oAuthSessionKey, user, authentication, SystemDateTime.now().plusSeconds(3600));
  }

  public OAuthSession addAttribute(HashMap<String, Object> attributes) {
    this.attributes.putAll(attributes);
    return new OAuthSession(
        oAuthSessionKey, user, authentication, SystemDateTime.now().plusSeconds(3600), attributes);
  }

  public boolean exists() {
    return Objects.nonNull(oAuthSessionKey) && oAuthSessionKey.exists();
  }

  public boolean hasAttribute(String key) {
    return attributes.containsKey(key);
  }

  public Object getAttribute(String key) {
    return attributes.get(key);
  }
}
