package org.idp.server.oauth.verifier;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.oauth.verifier.base.OidcRequestBaseVerifier;
import org.idp.server.type.oauth.RedirectUri;

public class FapiBaselineVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

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
  }

  /** shall require redirect URIs to be pre-registered; */
  void throwExceptionIfUnregisteredRedirectUri(OAuthRequestContext context) {
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!clientConfiguration.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require redirect URIs to be pre-registered");
    }
  }

  /** shall require the redirect_uri in the authorization request; */
  void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUriInRequest()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "When FAPI Baseline profile, shall require the redirect_uri in the authorization request");
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
              context.redirectUri().value()));
    }
  }
  // shall require user authentication to an appropriate Level of Assurance for the operations the
  // client will be authorized to perform on behalf of the user;
  // shall require explicit approval by the user to authorize the requested scope if it has not been
  // previously authorized;
  // shall reject an authorization code (Section 1.3.1 of RFC6749) if it has been previously used;
  // shall return token responses that conform to Section 4.1.4 of RFC6749;
  // shall return the list of granted scopes with the issued access token if the request was passed
  // in the front channel and was not integrity protected;
  // shall provide non-guessable access tokens, authorization codes, and refresh token (where
  // applicable), with sufficient entropy such that the probability of an attacker guessing the
  // generated token is computationally infeasible as per RFC6749 Section 10.10;
  // should clearly identify the details of the grant to the user during authorization as in 16.18
  // of OIDC;
  // should provide a mechanism for the end-user to revoke access tokens and refresh tokens granted
  // to a client as in 16.18 of OIDC;
  /** shall require redirect URIs to use the https scheme; */
  void throwExceptionIfNotHttpsRedirectUri(OAuthRequestContext context) {
    RedirectUri redirectUri = context.redirectUri();
    if (!redirectUri.isHttps()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "When FAPI Baseline profile, shall shall require redirect URIs to use the https scheme (%s)",
              context.redirectUri().value()));
    }
  }
}
