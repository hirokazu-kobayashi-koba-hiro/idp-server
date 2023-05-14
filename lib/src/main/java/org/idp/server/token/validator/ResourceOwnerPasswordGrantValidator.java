package org.idp.server.token.validator;

import org.idp.server.token.TokenRequestContext;
import org.idp.server.token.exception.TokenBadRequestException;
import org.idp.server.type.oauth.GrantType;

/**
 * 4.3.2. Access Token Request
 *
 * <p>The client makes a request to the token endpoint by adding the following parameters using the
 * "application/x-www-form-urlencoded" format per Appendix B with a character encoding of UTF-8 in
 * the HTTP request entity-body:
 *
 * <p>grant_type REQUIRED. Value MUST be set to "password".
 *
 * <p>username REQUIRED. The resource owner username.
 *
 * <p>password REQUIRED. The resource owner password.
 *
 * <p>scope OPTIONAL. The scope of the access request as described by Section 3.3.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-4.3.2">4.3.2. Access Token
 *     Request</a>
 */
public class ResourceOwnerPasswordGrantValidator {

  TokenRequestContext tokenRequestContext;

  public ResourceOwnerPasswordGrantValidator(TokenRequestContext tokenRequestContext) {
    this.tokenRequestContext = tokenRequestContext;
  }

  public void validate() {
    throwExceptionIfUnSupportedGrantTypeWithServer();
    throwExceptionIfUnSupportedGrantTypeWithClient();
    throwExceptionIfNotContainsUsername();
    throwExceptionIfNotContainsPassword();
    throwExceptionIfNotContainsClientId();
  }

  void throwExceptionIfUnSupportedGrantTypeWithServer() {
    if (!tokenRequestContext.isSupportedGrantTypeWithServer(GrantType.password)) {
      throw new TokenBadRequestException(
          "unsupported_grant_type",
          "this request grant_type is password, but authorization server does not support");
    }
  }

  void throwExceptionIfUnSupportedGrantTypeWithClient() {
    if (!tokenRequestContext.isSupportedGrantTypeWithClient(GrantType.password)) {
      throw new TokenBadRequestException(
          "unauthorized_client",
          "this request grant_type is password, but client does not support");
    }
  }

  void throwExceptionIfNotContainsUsername() {
    if (!tokenRequestContext.hasUsername()) {
      throw new TokenBadRequestException(
          "token request does not contains username, password grant must contains username");
    }
  }

  void throwExceptionIfNotContainsPassword() {
    if (!tokenRequestContext.hasPassword()) {
      throw new TokenBadRequestException(
          "token request does not contains password, password grant must contains password");
    }
  }

  void throwExceptionIfNotContainsClientId() {
    if (tokenRequestContext.hasClientSecretBasic()) {
      return;
    }
    if (!tokenRequestContext.hasClientId()) {
      throw new TokenBadRequestException(
          "token request does not contains client_id, password must contains client_id");
    }
  }
}
