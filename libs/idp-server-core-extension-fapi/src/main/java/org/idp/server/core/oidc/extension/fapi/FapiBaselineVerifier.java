/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.extension.fapi;

import org.idp.server.basic.type.oauth.ClientAuthenticationType;
import org.idp.server.basic.type.oauth.RedirectUri;
import org.idp.server.core.oidc.AuthorizationProfile;
import org.idp.server.core.oidc.OAuthRequestContext;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.exception.OAuthBadRequestException;
import org.idp.server.core.oidc.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.oidc.request.AuthorizationRequest;
import org.idp.server.core.oidc.verifier.AuthorizationRequestVerifier;
import org.idp.server.core.oidc.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.core.oidc.verifier.base.OidcRequestBaseVerifier;

public class FapiBaselineVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  public AuthorizationProfile profile() {
    return AuthorizationProfile.FAPI_BASELINE;
  }

  @Override
  public void verify(OAuthRequestContext context) {
    throwExceptionIfUnregisteredRedirectUri(context);
    throwExceptionIfNotContainsRedirectUri(context);
    throwExceptionUnMatchRedirectUri(context);
    throwExceptionIfNotHttpsRedirectUri(context);
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    throwExceptionIfClientSecretPostOrClientSecretBasic(context);
    throwExceptionIfNotS256CodeChallengeMethod(context);
    throwExceptionIfHasOpenidScopeAndNotContainsNonce(context);
    throwExceptionIfNotHasOpenidScopeAndNotContainsState(context);
  }

  /** shall require redirect URIs to be pre-registered; */
  void throwExceptionIfUnregisteredRedirectUri(OAuthRequestContext context) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require redirect URIs to be pre-registered",
          context.tenant());
    }
  }

  /** shall require the redirect_uri in the authorization request; */
  void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUriInRequest()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the redirect_uri in the authorization request",
          context.tenant());
    }
  }

  /**
   * shall require the value of redirect_uri to exactly match one of the pre-registered redirect
   * URIs;
   */
  void throwExceptionUnMatchRedirectUri(OAuthRequestContext context) {
    if (!context.isRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Baseline profile, shall require the value of redirect_uri to exactly match one of the pre-registered redirect URIs (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /** shall require redirect URIs to use the https scheme; */
  void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
    RedirectUri redirectUri = context.redirectUri();
    if (!redirectUri.isHttps()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Baseline profile, shall shall require redirect URIs to use the https scheme (%s)",
              context.redirectUri().value()),
          context.tenant());
    }
  }

  /**
   * shall authenticate the confidential client using one of the following methods: Mutual TLS for
   * OAuth Client Authentication as specified in Section 2 of MTLS, or client_secret_jwt or
   * private_key_jwt as specified in Section 9 of OIDC;
   */
  void throwExceptionIfClientSecretPostOrClientSecretBasic(OAuthRequestContext context) {
    ClientAuthenticationType clientAuthenticationType = context.clientAuthenticationType();
    if (clientAuthenticationType.isClientSecretBasic()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, client_secret_basic MUST not used",
          context);
    }
    if (clientAuthenticationType.isClientSecretPost()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          "When FAPI Baseline profile, client_secret_post MUST not used",
          context);
    }
  }

  /** shall require RFC7636 with S256 as the code challenge method; */
  void throwExceptionIfNotS256CodeChallengeMethod(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (!authorizationRequest.hasCodeChallenge()
        || !authorizationRequest.hasCodeChallengeMethod()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, authorization request must contains code_challenge and code_challenge_method(S256).",
          context);
    }
    if (!authorizationRequest.codeChallengeMethod().isS256()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require RFC7636 with S256 as the code challenge method.",
          context);
    }
  }

  /**
   * 5.2.2.2. Client requesting openid scope
   *
   * <p>If the client requests the openid scope, the authorization server shall require the nonce
   * parameter defined in Section 3.1.2.1 of OIDC in the authentication request.
   */
  void throwExceptionIfHasOpenidScopeAndNotContainsNonce(OAuthRequestContext context) {
    if (!context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasNonce()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request.",
          context);
    }
  }

  /**
   * 5.2.2.3. Clients not requesting openid scope
   *
   * <p>If the client does not requests the openid scope, the authorization server shall require the
   * state parameter defined in Section 4.1.1 of RFC6749.
   */
  void throwExceptionIfNotHasOpenidScopeAndNotContainsState(OAuthRequestContext context) {
    if (context.hasOpenidScope()) {
      return;
    }
    if (!context.authorizationRequest().hasState()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the nonce parameter defined in Section 3.1.2.1 of OIDC in the authentication request.",
          context);
    }
  }
}
