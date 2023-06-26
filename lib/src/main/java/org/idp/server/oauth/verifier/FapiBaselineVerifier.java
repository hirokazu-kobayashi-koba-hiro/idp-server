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
