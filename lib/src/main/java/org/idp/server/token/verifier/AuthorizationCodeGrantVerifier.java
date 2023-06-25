package org.idp.server.token.verifier;

import java.util.*;
import org.idp.server.oauth.AuthorizationProfile;
import org.idp.server.oauth.clientcredentials.ClientCredentials;
import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;

public class AuthorizationCodeGrantVerifier {
  TokenRequestContext tokenRequestContext;
  AuthorizationRequest authorizationRequest;
  AuthorizationCodeGrant authorizationCodeGrant;
  ClientCredentials clientCredentials;
  PkceVerifier pkceVerifier;
  static Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> baseVerifiers =
      new HashMap<>();

  static {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new AuthorizationCodeGrantBaseVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new AuthorizationCodeGrantBaseVerifier());
    baseVerifiers.put(
        AuthorizationProfile.FAPI_BASELINE, new AuthorizationCodeGrantFapiBaselineVerifier());
    baseVerifiers.put(
        AuthorizationProfile.FAPI_ADVANCE, new AuthorizationCodeGrantFapiAdvanceVerifier());
    baseVerifiers.put(AuthorizationProfile.UNDEFINED, new AuthorizationCodeGrantBaseVerifier());
  }

  public AuthorizationCodeGrantVerifier(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    this.tokenRequestContext = tokenRequestContext;
    this.authorizationRequest = authorizationRequest;
    this.authorizationCodeGrant = authorizationCodeGrant;
    this.clientCredentials = clientCredentials;
    this.pkceVerifier = new PkceVerifier(tokenRequestContext, authorizationRequest);
  }

  public void verify() {
    AuthorizationCodeGrantVerifierInterface baseVerifier =
        baseVerifiers.get(authorizationRequest.profile());
    if (Objects.isNull(baseVerifier)) {
      throw new RuntimeException("idp server does not supported profile");
    }
    baseVerifier.verify(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);
    pkceVerifier.verify();
  }
}
