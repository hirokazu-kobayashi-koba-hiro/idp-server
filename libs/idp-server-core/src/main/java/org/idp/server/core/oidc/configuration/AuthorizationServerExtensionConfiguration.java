package org.idp.server.core.oidc.configuration;

import java.util.*;
import org.idp.server.basic.json.JsonReadable;
import org.idp.server.core.oidc.configuration.mfa.MfaPolicy;

public class AuthorizationServerExtensionConfiguration implements JsonReadable {

  List<String> fapiBaselineScopes = new ArrayList<>();
  List<String> fapiAdvanceScopes = new ArrayList<>();
  int authorizationCodeValidDuration = 600;
  String tokenSignedKeyId = "";
  String idTokenSignedKeyId = "";
  long accessTokenDuration = 1800;
  long refreshTokenDuration = 3600;
  long idTokenDuration = 3600;
  boolean idTokenStrictMode = false;
  long defaultMaxAge = 86400;
  long authorizationResponseDuration = 60;
  int backchannelAuthRequestExpiresIn = 300;
  int backchannelAuthPollingInterval = 5;
  int oauthAuthorizationRequestExpiresIn = 1800;
  MfaPolicy mfaPolicy = new MfaPolicy();

  public AuthorizationServerExtensionConfiguration() {}

  public boolean hasFapiBaselineScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiBaselineScopes.contains(scope));
  }

  public boolean hasFapiAdvanceScope(Set<String> scopes) {
    return scopes.stream().anyMatch(scope -> fapiAdvanceScopes.contains(scope));
  }

  public int authorizationCodeValidDuration() {
    return authorizationCodeValidDuration;
  }

  public String tokenSignedKeyId() {
    return tokenSignedKeyId;
  }

  public String idTokenSignedKeyId() {
    return idTokenSignedKeyId;
  }

  public long accessTokenDuration() {
    return accessTokenDuration;
  }

  public long refreshTokenDuration() {
    return refreshTokenDuration;
  }

  public long idTokenDuration() {
    return idTokenDuration;
  }

  public boolean isIdTokenStrictMode() {
    return idTokenStrictMode;
  }

  public long defaultMaxAge() {
    return defaultMaxAge;
  }

  public long authorizationResponseDuration() {
    return authorizationResponseDuration;
  }

  public int backchannelAuthRequestExpiresIn() {
    return backchannelAuthRequestExpiresIn;
  }

  public int backchannelAuthPollingInterval() {
    return backchannelAuthPollingInterval;
  }

  public int oauthAuthorizationRequestExpiresIn() {
    return oauthAuthorizationRequestExpiresIn;
  }

  public List<String> availableAuthenticationMethods() {
    if (mfaPolicy == null) {
      return List.of("password");
    }
    return mfaPolicy.availableMethods();
  }

  public List<String> fapiBaselineScopes() {
    return fapiBaselineScopes;
  }

  public List<String> fapiAdvanceScopes() {
    return fapiAdvanceScopes;
  }

  public boolean idTokenStrictMode() {
    return idTokenStrictMode;
  }

  public MfaPolicy mfaPolicy() {
    return mfaPolicy;
  }
}
