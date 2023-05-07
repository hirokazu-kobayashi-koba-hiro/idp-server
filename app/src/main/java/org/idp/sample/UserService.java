package org.idp.sample;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.idp.server.ciba.CibaRequestDelegate;
import org.idp.server.ciba.UserCriteria;
import org.idp.server.ciba.request.BackchannelAuthenticationRequest;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.ciba.UserCode;
import org.springframework.stereotype.Service;

@Service
public class UserService implements CibaRequestDelegate {

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
  public void notify(User user, BackchannelAuthenticationRequest request) {
    // TODO official implementation
  }
}
