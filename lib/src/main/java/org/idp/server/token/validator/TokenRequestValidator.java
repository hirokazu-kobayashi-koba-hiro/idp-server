package org.idp.server.token.validator;

import java.util.List;
import org.idp.server.token.TokenRequestParameters;
import org.idp.server.token.exception.TokenBadRequestException;

public class TokenRequestValidator {
  TokenRequestParameters parameters;

  public TokenRequestValidator(TokenRequestParameters parameters) {
    this.parameters = parameters;
  }

  public void validate() {
    throwIfNotContainsGrantType();
    throwIfDuplicateValue();
  }

  void throwIfNotContainsGrantType() {
    if (!parameters.hasGrantType()) {
      throw new TokenBadRequestException(
          "token request must contains grant_type, but this request does not contains grant_type");
    }
  }

  /**
   * 3.2. Token Endpoint validation
   *
   * <p>Request and response parameters MUST NOT be included more than once.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.2">3.2. Authorization
   *     Endpoint</a>
   */
  void throwIfDuplicateValue() {
    List<String> keys = parameters.multiValueKeys();
    if (!keys.isEmpty()) {
      String keysValue = String.join(" ", keys);
      throw new TokenBadRequestException(
          String.format("token request must not contains duplicate value; keys (%s)", keysValue));
    }
  }
}
