package org.idp.server.core.token.verifier;

import org.idp.server.core.oauth.clientcredentials.ClientCredentials;
import org.idp.server.core.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.token.TokenRequestContext;
import org.idp.server.core.token.exception.TokenBadRequestException;
import org.idp.server.core.type.oauth.GrantType;

public class AuthorizationCodeGrantBaseVerifier implements AuthorizationCodeGrantVerifierInterface {

  public void verify(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant,
      ClientCredentials clientCredentials) {
    throwExceptionIfUnSupportedGrantTypeWithServer(tokenRequestContext);
    throwExceptionIfUnSupportedGrantTypeWithClient(tokenRequestContext);
    throwExceptionIfNotFoundAuthorizationCode(
        tokenRequestContext, authorizationRequest, authorizationCodeGrant);
    throwExceptionIfUnMatchRedirectUri(tokenRequestContext, authorizationRequest);
  }

  /**
   * 5.2. Error Response unsupported_grant_type
   *
   * <p>The authorization grant type is not supported by the authorization server.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfUnSupportedGrantTypeWithServer(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is authorization_code, but authorization server does not support");
    }
  }

  /**
   * 5.2. Error Response unauthorized_client
   *
   * <p>The authenticated client is not authorized to use this authorization grant type.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfUnSupportedGrantTypeWithClient(TokenRequestContext tokenRequestContext) {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.authorization_code)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is authorization_code, but client does not support");
    }
  }

  /**
   * 4.1.3. Access Token Request verification
   *
   * <p>ensure that the authorization code was issued to the authenticated confidential client, or
   * if the client is public, ensure that the code was issued to "client_id" in the request,
   *
   * <p>verify that the authorization code is valid
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
   *     Request</a>
   */
  void throwExceptionIfNotFoundAuthorizationCode(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    if (!authorizationCodeGrant.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("not found authorization code (%s)", tokenRequestContext.code().value()));
    }
    if (!authorizationRequest.exists()) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("not found authorization code (%s)", tokenRequestContext.code().value()));
    }
    if (!authorizationCodeGrant.isGrantedClient(tokenRequestContext.clientIdentifier())) {
      throw new TokenBadRequestException(
          "invalid_grant",
          String.format("not found authorization code (%s)", tokenRequestContext.code().value()));
    }
  }

  /**
   * 4.1.3. Access Token Request verification
   *
   * <p>ensure that the "redirect_uri" parameter is present if the "redirect_uri" parameter was
   * included in the initial authorization request as described in Section 4.1.1, and if included
   * ensure that their values are identical.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.1.3">4.1.3. Access Token
   *     Request</a>
   */
  void throwExceptionIfUnMatchRedirectUri(
      TokenRequestContext tokenRequestContext, AuthorizationRequest authorizationRequest) {
    if (!authorizationRequest.hasRedirectUri()) {
      return;
    }
    if (!authorizationRequest.redirectUri().equals(tokenRequestContext.redirectUri())) {
      throw new TokenBadRequestException(
          String.format(
              "token request redirect_uri does not equals to authorization request redirect_uri (%s)",
              tokenRequestContext.redirectUri().value()));
    }
  }
}
