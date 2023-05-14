package org.idp.server.token.verifier;

import org.idp.server.oauth.grant.AuthorizationCodeGrant;
import org.idp.server.oauth.request.AuthorizationRequest;
import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

public class TokenRequestCodeGrantBaseVerifier {

  TokenRequestContext tokenRequestContext;
  AuthorizationRequest authorizationRequest;
  AuthorizationCodeGrant authorizationCodeGrant;

  public TokenRequestCodeGrantBaseVerifier(
      TokenRequestContext tokenRequestContext,
      AuthorizationRequest authorizationRequest,
      AuthorizationCodeGrant authorizationCodeGrant) {
    this.tokenRequestContext = tokenRequestContext;
    this.authorizationRequest = authorizationRequest;
    this.authorizationCodeGrant = authorizationCodeGrant;
  }

  public void verify() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotFoundAuthorizationCode();
    throwExceptionIfUnMatchRedirectUri();
  }

  /**
   * 5.2. Error Response unsupported_grant_type
   *
   * <p>The authorization grant type is not supported by the authorization server.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-5.2">5.2. Error Response</a>
   */
  void throwExceptionIfUnSupportedGrantTypeWithServer() {
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
  void throwExceptionIfUnSupportedGrantTypeWithClient() {
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
  void throwExceptionIfNotFoundAuthorizationCode() {
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
    if (!authorizationCodeGrant.isGrantedClient(tokenRequestContext.clientId())) {
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
  void throwExceptionIfUnMatchRedirectUri() {
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
