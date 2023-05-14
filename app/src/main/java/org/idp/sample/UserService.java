package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.identity.User;
import org.idp.server.token.PasswordCredentialsGrantDelegate;
import org.idp.server.type.ciba.UserCode;
import org.idp.server.type.extension.CustomProperties;
import org.springframework.stereotype.Service;

@Service
public class UserService implements CibaRequestDelegate, PasswordCredentialsGrantDelegate {

  @Override
  public User find(UserCriteria criteria) {
    // TODO official implementation
    if (!"001".equals(criteria.loginHint().value())) {
      return new User();
    }
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

  @Override
  public boolean authenticate(User user, UserCode userCode) {
    // TODO official implementation
    return "successUserCode".equals(userCode.value());
  }

  @Override
  public CustomProperties getCustomProperties(User user, BackchannelAuthenticationRequest request) {
    // TODO official implementation
    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }

  @Override
  public void notify(User user, BackchannelAuthenticationRequest request) {
    // TODO official implementation
  }

  @Override
  public User findAndAuthenticate(String username, String password) {
    // TODO official implementation
    if (!"001".equals(username)) {
      return new User();
    }
    if (!"successUserCode".equals(password)) {
      return new User();
    }
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

  @Override
  public CustomProperties getCustomProperties(User user) {
    // TODO official implementation
    Map<String, Object> values = Map.of("custom", UUID.randomUUID().toString());
    return new CustomProperties(values);
  }
}
