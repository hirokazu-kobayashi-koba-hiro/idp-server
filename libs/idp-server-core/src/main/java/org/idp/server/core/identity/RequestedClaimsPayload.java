package org.idp.server.core.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

public class RequestedClaimsPayload implements JsonReadable {
  RequestedUserinfoClaims userinfo = new RequestedUserinfoClaims();
  RequestedIdTokenClaims idToken = new RequestedIdTokenClaims();

  public RequestedClaimsPayload() {}

  public RequestedUserinfoClaims userinfo() {
    return userinfo;
  }

  public RequestedIdTokenClaims idToken() {
    return idToken;
  }

  public boolean hasUserinfo() {
    return Objects.nonNull(userinfo);
  }

  public boolean hasIdToken() {
    return Objects.nonNull(idToken);
  }

  public boolean exists() {
    return hasUserinfo() || hasIdToken();
  }

  public List<String> userinfoClaims() {
    List<String> claims = new ArrayList<>();
    if (hasUserinfo()) {
      return claims;
    }

    if (userinfo.hasSub()) claims.add("sub");
    if (userinfo.hasName()) claims.add("name");
    if (userinfo.hasGivenName()) claims.add("given_name");
    if (userinfo.hasMiddleName()) claims.add("middle_name");
    if (userinfo.hasNickname()) claims.add("nickname");
    if (userinfo.hasPreferredUsername()) claims.add("preferred_username");
    if (userinfo.hasProfile()) claims.add("profile");
    if (userinfo.hasPicture()) claims.add("picture");
    if (userinfo.hasWebsite()) claims.add("website");
    if (userinfo.hasEmail()) claims.add("email");
    if (userinfo.hasEmailVerified()) claims.add("email_verified");
    if (userinfo.hasGender()) claims.add("gender");
    if (userinfo.hasBirthdate()) claims.add("birthdate");
    if (userinfo.hasZoneinfo()) claims.add("zoneinfo");
    if (userinfo.hasLocale()) claims.add("locale");
    if (userinfo.hasPhoneNumber()) claims.add("phone_number");
    if (userinfo.hasPhoneNumberVerified()) claims.add("phone_number_verified");
    if (userinfo.hasAddress()) claims.add("address");
    if (userinfo.hasUpdatedAt()) claims.add("updated_at");

    return claims;
  }

  public List<String> idTokenClaims() {
    List<String> claims = new ArrayList<>();
    if (hasIdToken()) {
      return claims;
    }

    if (idToken.hasSub()) claims.add("sub");
    if (idToken.hasName()) claims.add("name");
    if (idToken.hasGivenName()) claims.add("given_name");
    if (idToken.hasMiddleName()) claims.add("middle_name");
    if (idToken.hasNickname()) claims.add("nickname");
    if (idToken.hasPreferredUsername()) claims.add("preferred_username");
    if (idToken.hasProfile()) claims.add("profile");
    if (idToken.hasPicture()) claims.add("picture");
    if (idToken.hasWebsite()) claims.add("website");
    if (idToken.hasEmail()) claims.add("email");
    if (idToken.hasEmailVerified()) claims.add("email_verified");
    if (idToken.hasGender()) claims.add("gender");
    if (idToken.hasBirthdate()) claims.add("birthdate");
    if (idToken.hasZoneinfo()) claims.add("zoneinfo");
    if (idToken.hasLocale()) claims.add("locale");
    if (idToken.hasPhoneNumber()) claims.add("phone_number");
    if (idToken.hasPhoneNumberVerified()) claims.add("phone_number_verified");
    if (idToken.hasAddress()) claims.add("address");
    if (idToken.hasUpdatedAt()) claims.add("updated_at");

    return claims;
  }
}
