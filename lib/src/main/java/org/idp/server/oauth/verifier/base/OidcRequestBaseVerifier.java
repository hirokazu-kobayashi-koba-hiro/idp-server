package org.idp.server.oauth.verifier.base;

import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.oidc.Prompts;

/**
 * 3.1.2.2. Authentication Request Validation
 *
 * <p>The Authorization Server MUST validate the request received as follows:
 *
 * <p>The Authorization Server MUST validate all the OAuth 2.0 parameters according to the OAuth 2.0
 * specification. Verify that a scope parameter is present and contains the openid scope value. (If
 * no openid scope value is present, the request may still be a valid OAuth 2.0 request, but is not
 * an OpenID Connect request.) The Authorization Server MUST verify that all the REQUIRED parameters
 * are present and their usage conforms to this specification. If the sub (subject) Claim is
 * requested with a specific value for the ID Token, the Authorization Server MUST only send a
 * positive response if the End-User identified by that sub value has an active session with the
 * Authorization Server or has been Authenticated as a result of the request. The Authorization
 * Server MUST NOT reply with an ID Token or Access Token for a different user, even if they have an
 * active session with the Authorization Server. Such a request can be made either using an
 * id_token_hint parameter or by requesting a specific Claim Value as described in Section 5.5.1, if
 * the claims parameter is supported by the implementation. As specified in OAuth 2.0 [RFC6749],
 * Authorization Servers SHOULD ignore unrecognized request parameters.
 *
 * <p>If the Authorization Server encounters any error, it MUST return an error response, per
 * Section 3.1.2.6.
 *
 * @see <a
 *     href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequestValidation">3.1.2.2.
 *     Authentication Request Validation</a>
 */
public class OidcRequestBaseVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier baseVerifier = new OAuthRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    throwExceptionIfNotContainsRedirectUri(context);
    throwExceptionIfUnRegisteredRedirectUri(context);
    throwExceptionIfHttpRedirectUriAndImplicitFlow(context);
    throwExceptionIfNotContainsNonceAndImplicitFlowOrHybridFlow(context);
    baseVerifier.verify(context);
    throwExceptionIfInvalidDisplay(context);
    throwExceptionIfInvalidPrompt(context);
    throwExceptionIfInvalidPromptNonePattern(context);
    throwExceptionIfInvalidMaxAge(context);
  }

  /**
   * redirect_uri REQUIRED.
   *
   * <p>Redirection URI to which the response will be sent.
   *
   * @param context
   */
  void throwExceptionIfNotContainsRedirectUri(OAuthRequestContext context) {
    if (!context.hasRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request", "oidc profile authorization request must contains redirect_uri param");
    }
  }

  /**
   * redirect_uri Simple String Comparison
   *
   * <p>This URI MUST exactly match one of the Redirection URI values for the Client pre-registered
   * at the OpenID Provider, with the matching performed as described in Section 6.2.1 of [RFC3986]
   * (Simple String Comparison).
   *
   * @param context
   */
  void throwExceptionIfUnRegisteredRedirectUri(OAuthRequestContext context) {
    if (!context.isRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request redirect_uri does not register in client configuration (%s)",
              context.redirectUri().value()));
    }
  }

  void throwExceptionIfHttpRedirectUriAndImplicitFlow(OAuthRequestContext context) {
    if (!context.isOidcImplicitFlow()) {
      return;
    }
    if (!context.isWebApplication()) {
      return;
    }
    if (context.redirectUri().isHttp()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "When using this flow and client application is web application, the Redirection URI MUST NOT use the http scheme (%s)",
              context.redirectUri().value()),
          context);
    }
  }

  void throwExceptionIfNotContainsNonceAndImplicitFlowOrHybridFlow(OAuthRequestContext context) {
    if (!context.isOidcImplicitFlowOrHybridFlow()) {
      return;
    }
    if (!context.authorizationRequest().hasNonce()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          "When using implicit flow or hybrid flow, authorization request must contains nonce.",
          context);
    }
  }

  /**
   * display OPTIONAL.
   *
   * <p>ASCII string value that specifies how the Authorization Server displays the authentication
   * and consent user interface pages to the End-User. The defined values are:
   *
   * <p>page The Authorization Server SHOULD display the authentication and consent UI consistent
   * with a full User Agent page view. If the display parameter is not specified, this is the
   * default display mode.
   *
   * <p>popup The Authorization Server SHOULD display the authentication and consent UI consistent
   * with a popup User Agent window. The popup User Agent window should be of an appropriate size
   * for a login-focused dialog and should not obscure the entire window that it is popping up over.
   *
   * <p>touch The Authorization Server SHOULD display the authentication and consent UI consistent
   * with a device that leverages a touch interface.
   *
   * <p>wap The Authorization Server SHOULD display the authentication and consent UI consistent
   * with a "feature phone" type display.
   *
   * <p>The Authorization Server MAY also attempt to detect the capabilities of the User Agent and
   * present an appropriate display.
   *
   * @param context
   */
  void throwExceptionIfInvalidDisplay(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidDisplay()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request display is defined that page, popup, touch, wap, but request display is (%s)",
              context.getParams(OAuthRequestKey.display)),
          context);
    }
  }

  void throwExceptionIfInvalidPrompt(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidPrompt()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request prompt is defined that none, login, consent, select_account, but request prompt is (%s)",
              context.getParams(OAuthRequestKey.prompt)),
          context);
    }
  }

  /**
   * The prompt parameter can be used by the Client to make sure that the End-User is still present
   * for the current session or to bring attention to the request. If this parameter contains none
   * with any other value, an error is returned.
   *
   * @param context
   */
  void throwExceptionIfInvalidPromptNonePattern(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    Prompts prompts = authorizationRequest.prompts();
    if (prompts.hasNone() && prompts.isMultiValue()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request must not contains none with any other (%s)",
              context.getParams(OAuthRequestKey.prompt)),
          context);
    }
  }

  void throwExceptionIfInvalidMaxAge(OAuthRequestContext context) {
    AuthorizationRequest authorizationRequest = context.authorizationRequest();
    if (authorizationRequest.isInvalidMaxAge()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "authorization request max_age is invalid (%s)",
              context.getParams(OAuthRequestKey.max_age)),
          context);
    }
  }
}
