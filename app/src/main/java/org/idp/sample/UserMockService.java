package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.OAuthRequestDelegate;
import org.idp.server.oauth.identity.User;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.PasswordCredentialsGrantDelegate;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.extension.CustomProperties;
import org.idp.server.type.extension.SessionIdentifier;
import org.idp.server.type.oauth.Password;
import org.idp.server.type.oauth.TokenIssuer;
import org.idp.server.type.oauth.Username;
import org.springframework.stereotype.Service;

@Service
public class UserMockService
    implements CibaRequestDelegate, PasswordCredentialsGrantDelegate, OAuthRequestDelegate {

  @Override
  public User find(TokenIssuer tokenIssuer, UserCriteria criteria) {

    if (!"001".equals(criteria.loginHint().value())) {
      return new User();
    }
    return getUser();
  }

  @Override
  public boolean authenticate(TokenIssuer tokenIssuer, User user, UserCode userCode) {

    return "successUserCode".equals(userCode.value());
  }

  @Override
  public CustomProperties getCustomProperties(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {

    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public void notify(
      TokenIssuer tokenIssuer, User user, BackchannelAuthenticationRequest request) {}

  @Override
  public User findAndAuthenticate(TokenIssuer tokenIssuer, Username username, Password password) {
    if (!"001".equals(username.value())) {
      return new User();
    }
    if (!"successUserCode".equals(password.value())) {
      return new User();
    }
    return getUser();
  }

  @Override
  public CustomProperties getCustomProperties(TokenIssuer tokenIssuer, User user) {
    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public boolean isValidSession(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      AuthorizationRequest authorizationRequest) {
    return sessionIdentifier.exists();
  }

  @Override
  public User getUser(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      AuthorizationRequest authorizationRequest) {
    return getUser();
  }

  @Override
  public CustomProperties getCustomProperties(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      User user,
      AuthorizationRequest authorizationRequest) {
    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public void registerSession(
      SessionIdentifier sessionIdentifier,
      TokenIssuer tokenIssuer,
      User user,
      AuthorizationRequest authorizationRequest) {}

  public User getUser() {
    return new User()
        .setSub("001")
        .setName("ito ichiro")
        .setGivenName("ichiro")
        .setFamilyName("ito")
        .setNickname("ito")
        .setPreferredUsername("ichiro")
        .setProfile("https://example.com/profiles/123")
        .setPicture("https://example.com/pictures/123")
        .setWebsite("https://example.com")
        .setEmail("ito.ichiro@gmail.com")
        .setEmailVerified(true)
        .setGender("other")
        .setBirthdate("2000-02-02")
        .setZoneinfo("ja-jp")
        .setLocale("locale")
        .setPhoneNumber("09012345678")
        .setPhoneNumberVerified(false)
        .setUpdateAt(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));
  }
}
