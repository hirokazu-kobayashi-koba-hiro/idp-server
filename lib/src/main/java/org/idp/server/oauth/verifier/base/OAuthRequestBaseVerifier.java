package org.idp.server.oauth.verifier.base;

import org.idp.server.basic.http.InvalidUriException;
import org.idp.server.basic.http.UriWrapper;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.type.OAuthRequestKey;
import org.idp.server.type.extension.RegisteredRedirectUris;
import org.idp.server.type.oauth.ResponseType;
import org.idp.server.type.oauth.Scopes;

/**
 * oauth2.0 base verifier
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749">RFC6749</a>
 */
public class OAuthRequestBaseVerifier implements AuthorizationRequestVerifier {

  @Override
  public void verify(OAuthRequestContext context) {
    throwIfInvalidRedirectUri(context);
    throwIfMissingResponseType(context);
    throwIfInvalidResponseType(context);
    throwIfUnSupportedResponseType(context);
    throwIfNotContainsValidScope(context);
  }

  /**
   * 3.1.2.4. Invalid Endpoint
   *
   * <p>If an authorization request fails validation due to a missing, invalid, or mismatching
   * redirection URI, the authorization server SHOULD inform the resource owner of the error and
   * MUST NOT automatically redirect the user-agent to the invalid redirection URI.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2.4">3.1.2.4. Invalid
   *     Endpoint</a>
   */
  void throwIfInvalidRedirectUri(OAuthRequestContext context) {
    if (context.hasRedirectUri()) {
      throwIfRedirectUriContainsFragment(context);
      throwIfUnMatchRedirectUri(context);
    } else {
      throwIfMultiRegisteredRedirectUri(context);
    }
  }

  /**
   * The redirection endpoint URI MUST be an absolute URI as defined by [RFC3986] Section 4.3. The
   * endpoint URI MAY include an "application/x-www-form-urlencoded" formatted (per Appendix B)
   * query component ([RFC3986] Section 3.4), which MUST be retained when adding additional query
   * parameters. The endpoint URI MUST NOT include a fragment component.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2">3.1.2. Redirection
   *     Endpoint</a>
   */
  void throwIfRedirectUriContainsFragment(OAuthRequestContext context) {
    try {
      UriWrapper uri = new UriWrapper(context.redirectUri().value());
      if (uri.hasFragment()) {
        throw new OAuthBadRequestException(
            "invalid_request",
            String.format("redirect_uri must not fragment (%s)", context.redirectUri().value()));
      }
    } catch (InvalidUriException exception) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request redirect_uri is invalid (%s)", context.redirectUri().value()));
    }
  }

  /**
   * 3.1.2.3. Dynamic Configuration
   *
   * <p>If multiple redirection URIs have been registered, if only part of the redirection URI has
   * been registered, or if no redirection URI has been registered, the client MUST include a
   * redirection URI with the authorization request using the "redirect_uri" request parameter.
   *
   * <p>When a redirection URI is included in an authorization request, the authorization server
   * MUST compare and match the value received against at least one of the registered redirection
   * URIs (or URI components) as defined in [RFC3986] Section 6, if any redirection URIs were
   * registered. If the client registration included the full redirection URI, the authorization
   * server MUST compare the two URIs using simple string comparison as defined in [RFC3986] Section
   * 6.2.1.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2.3">3.1.2.3. Dynamic
   *     Configuration</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc3986.html">rfc3986</a>
   */
  void throwIfUnMatchRedirectUri(OAuthRequestContext context) {
    RegisteredRedirectUris registeredRedirectUris = context.registeredRedirectUris();
    if (!registeredRedirectUris.containsWithNormalizationAndComparison(
        context.redirectUri().value())) {
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request redirect_uri does not match registered redirect uris (%s)",
              context.redirectUri().value()));
    }
  }

  /**
   * 3.1.2.3. Dynamic Configuration
   *
   * <p>If multiple redirection URIs have been registered, if only part of the redirection URI has
   * been registered, or if no redirection URI has been registered, the client MUST include a
   * redirection URI with the authorization request using the "redirect_uri" request parameter.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2.3">3.1.2.3. Dynamic
   *     Configuration</a>
   */
  void throwIfMultiRegisteredRedirectUri(OAuthRequestContext context) {
    if (context.isMultiRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "on multiple registered redirect uris, authorization request redirect_uri must contains");
    }
  }

  /**
   * 3.1.1. Response Type REQUIRED.
   *
   * <p>The value MUST be one of "code" for requesting an authorization code as described by Section
   * 4.1.1, "token" for requesting an access token (implicit grant) as described by Section 4.2.1,
   * or a registered extension value as described by Section 8.4.
   *
   * <p>If an authorization request is missing the "response_type" parameter,
   *
   * @param context
   */
  void throwIfMissingResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (responseType.isUndefined()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request", "response type is required in authorization request", context);
    }
  }

  /**
   * 3.1.1. Response Type REQUIRED.
   *
   * <p>if the response type is not understood, the authorization server MUST return an error
   * response as described in Section 4.1.2.1.
   *
   * @param context
   */
  void throwIfInvalidResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (responseType.isUnknown()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "response type is unknown type (%s)",
              context.getParams(OAuthRequestKey.response_type)),
          context);
    }
  }

  /**
   * response_type values that this OP supports.
   *
   * <p>4.1.2.1. Error Response unauthorized_client The client is not authorized to request an
   * authorization code using this method.
   *
   * @param context
   * @see <a
   *     href="https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata">OpenID
   *     Provider Metadata</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">4.1.2.1. Error
   *     Response</a>
   */
  void throwIfUnSupportedResponseType(OAuthRequestContext context) {
    ResponseType responseType = context.responseType();
    if (!context.isSupportedResponseTypeWithServer()) {
      throw new OAuthRedirectableBadRequestException(
          "unsupported_response_type",
          String.format(
              "authorization server is unsupported response_type (%s)", responseType.name()),
          context);
    }

    if (!context.isSupportedResponseTypeWithClient()) {
      throw new OAuthRedirectableBadRequestException(
          "unauthorized_client",
          String.format("client is unauthorized response_type (%s)", responseType.name()),
          context);
    }
  }

  /**
   * 3.3. Access Token Scope
   *
   * <p>If the client omits the scope parameter when requesting authorization, the authorization
   * server MUST either process the request using a pre-defined default value or fail the request
   * indicating an invalid scope. The authorization server SHOULD document its scope requirements
   * and default value (if defined).
   *
   * <p>invalid_scope The requested scope is invalid, unknown, or malformed.
   *
   * @param context
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.3">3.3. Access Token Scope</a>
   * @see <a herf="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.2.1">invalid_scope</a>
   */
  void throwIfNotContainsValidScope(OAuthRequestContext context) {
    Scopes scopes = context.scopes();
    if (!scopes.exists()) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_scope",
          String.format(
              "authorization request does not contains valid scope (%s)",
              context.getParams(OAuthRequestKey.scope)),
          context);
    }
  }
}
