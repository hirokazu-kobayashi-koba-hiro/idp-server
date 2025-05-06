package org.idp.server.core.token.verifier;

import java.util.*;
import org.idp.server.basic.exception.UnSupportedException;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.grant.AuthorizationCodeGrant;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.token.TokenRequestContext;

public class AuthorizationCodeGrantVerifier {
  TokenRequestContext tokenRequestContext;
  AuthorizationRequest authorizationRequest;
  AuthorizationCodeGrant authorizationCodeGrant;
  ClientCredentials clientCredentials;
  PkceVerifier pkceVerifier;
  static Map<AuthorizationProfile, AuthorizationCodeGrantVerifierInterface> baseVerifiers = new HashMap<>();

  static {
    baseVerifiers.put(AuthorizationProfile.OAUTH2, new AuthorizationCodeGrantBaseVerifier());
    baseVerifiers.put(AuthorizationProfile.OIDC, new AuthorizationCodeGrantBaseVerifier());
    baseVerifiers.put(AuthorizationProfile.FAPI_BASELINE, new AuthorizationCodeGrantFapiBaselineVerifier());
    baseVerifiers.put(AuthorizationProfile.FAPI_ADVANCE, new AuthorizationCodeGrantFapiAdvanceVerifier());
    baseVerifiers.put(AuthorizationProfile.UNDEFINED, new AuthorizationCodeGrantBaseVerifier());
  }

  public AuthorizationCodeGrantVerifier(TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest, AuthorizationCodeGrant authorizationCodeGrant, ClientCredentials clientCredentials) {
    this.tokenRequestContext = tokenRequestContext;
    this.authorizationRequest = authorizationRequest;
    this.authorizationCodeGrant = authorizationCodeGrant;
    this.clientCredentials = clientCredentials;
    this.pkceVerifier = new PkceVerifier(tokenRequestContext, authorizationRequest);
  }

  public void verify() {
    AuthorizationCodeGrantVerifierInterface baseVerifier = baseVerifiers.get(authorizationRequest.profile());
    if (Objects.isNull(baseVerifier)) {
      throw new UnSupportedException(String.format("idp server does not supported profile (%s)", authorizationRequest.profile().name()));
    }
    baseVerifier.verify(tokenRequestContext, authorizationRequest, authorizationCodeGrant, clientCredentials);
    pkceVerifier.verify();
  }
}
