package org.idp.server.oauth.verifier;

import org.idp.server.basic.http.InvalidUriException;
import org.idp.server.basic.http.UriWrapper;
import org.idp.server.oauth.OAuthRequestContext;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.oauth.verifier.base.AuthorizationRequestVerifier;
import org.idp.server.oauth.verifier.base.OAuthRequestBaseVerifier;
import org.idp.server.type.extension.RegisteredRedirectUris;

public class OAuth2RequestVerifier implements AuthorizationRequestVerifier {

  OAuthRequestBaseVerifier baseVerifier = new OAuthRequestBaseVerifier();

  @Override
  public void verify(OAuthRequestContext context) {
    throwExceptionIfInvalidRedirectUri(context);
    baseVerifier.verify(context);
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
  void throwExceptionIfInvalidRedirectUri(OAuthRequestContext context) {
    if (context.hasRedirectUriInRequest()) {
      throwExceptionIfRedirectUriContainsFragment(context);
      throwExceptionIfUnMatchRedirectUri(context);
    } else {
      throwExceptionIfMultiRegisteredRedirectUri(context);
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
  void throwExceptionIfRedirectUriContainsFragment(OAuthRequestContext context) {
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
  void throwExceptionIfUnMatchRedirectUri(OAuthRequestContext context) {
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
  void throwExceptionIfMultiRegisteredRedirectUri(OAuthRequestContext context) {
    if (context.isMultiRegisteredRedirectUri()) {
      throw new OAuthBadRequestException(
          "invalid_request",
          "on multiple registered redirect uris, authorization request redirect_uri must contains");
    }
  }
}
