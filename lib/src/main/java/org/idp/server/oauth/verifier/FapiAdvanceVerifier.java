package org.idp.server.oauth.verifier;

import java.util.Date;
import java.util.List;
import org.idp.server.basic.jose.JoseContext;
import org.idp.server.basic.jose.JsonWebTokenClaims;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.oauth.verifier.base.OidcRequestBaseVerifier;

public class FapiAdvanceVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier oAuthRequestBaseVerifier = new OAuthRequestBaseVerifier();
  OidcRequestBaseVerifier oidcRequestBaseVerifier = new OidcRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    if (context.isOidcRequest()) {
      oidcRequestBaseVerifier.verify(context);
    } else {
      oAuthRequestBaseVerifier.verify(context);
    }
    throwExceptionIfNotRRequestParameterPattern(context);
    throwExceptionIfInvalidResponseTypeAndResponseMode(context);
    throwIfNotSenderConstrainedAccessToken(context);
    throwExceptionIfNotContainExpAnd(context);
    throwExceptionIfNotContainsAud(context);
  }

  /**
   * shall require a JWS signed JWT request object passed by value with the request parameter or by
   * reference with the request_uri parameter;
   */
  void throwExceptionIfNotRRequestParameterPattern(OAuthRequestContext context) {
    if (!context.isRequestParameterPattern()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall require a JWS signed JWT request object passed by value with the request parameter or by reference with the request_uri parameter",
          context);
    }
  }

  /**
   * shall require the response_type value code id_token, or the response_type value code in
   * conjunction with the response_mode value jwt;
   */
  void throwExceptionIfInvalidResponseTypeAndResponseMode(OAuthRequestContext context) {
    if (context.responseType().isCodeIdToken()) {
      return;
    }
    if (context.responseType().isCode() && context.responseMode().isJwt()) {
      return;
    }
    throw new OAuthRedirectableBadRequestException(
        "invalid_request",
        "When FAPI Advance profile, shall require the response_type value code id_token, or the response_type value code in conjunction with the response_mode value jwt",
        context);
  }

  /**
   * shall only issue sender-constrained access tokens;
   *
   * <p>shall support MTLS as mechanism for constraining the legitimate senders of access tokens;
   */
  void throwIfNotSenderConstrainedAccessToken(OAuthRequestContext context) {
    ServerConfiguration serverConfiguration = context.serverConfiguration();
    ClientConfiguration clientConfiguration = context.clientConfiguration();
    if (!serverConfiguration.isTlsClientCertificateBoundAccessTokens()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall only issue sender-constrained access tokens, but server tls_client_certificate_bound_access_tokens is false",
          context);
    }
    if (!clientConfiguration.isTlsClientCertificateBoundAccessTokens()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When FAPI Advance profile, shall only issue sender-constrained access tokens, but client tls_client_certificate_bound_access_tokens is false",
          context);
    }
  }

  /**
   * shall require the request object to contain an exp claim that has a lifetime of no longer than
   * 60 minutes after the nbf claim;
   */
  void throwExceptionIfNotContainExpAnd(OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasExp()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an exp claim",
          context);
    }
    if (!claims.hasNbf()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an nbf claim",
          context);
    }
    Date exp = claims.getExp();
    Date nbf = claims.getNbf();
    if (exp.getTime() - nbf.getTime() > 3600001) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an exp claim that has a lifetime of no longer than 60 minutes after the nbf claim",
          context);
    }
  }

  /**
   * shall require the aud claim in the request object to be, or to be an array containing, the OP's
   * Issuer Identifier URL;
   */
  void throwExceptionIfNotContainsAud(OAuthRequestContext context) {
    JoseContext joseContext = context.joseContext();
    JsonWebTokenClaims claims = joseContext.claims();
    if (!claims.hasAud()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          "When FAPI Advance profile, shall require the request object to contain an aud claim",
          context);
    }
    List<String> aud = claims.getAud();
    if (!aud.contains(context.tokenIssuer().value())) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request_object",
          String.format(
              "When FAPI Advance profile, shall require the aud claim in the request object to be, or to be an array containing, the OP's Issuer Identifier URL (%s)",
              String.join(" ", aud)),
          context);
    }
  }
}
