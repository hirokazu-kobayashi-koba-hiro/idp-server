package org.idp.server.oauth.validator;

import java.util.List;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.exception.OAuthBadRequestException;

/**
 * validator
 *
 * <p>If an authorization request fails validation due to a missing, invalid, or mismatching
 * redirection URI, the authorization server SHOULD inform the resource owner of the error and MUST
 * NOT automatically redirect the user-agent to the invalid redirection URI.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1.2.4">3.1.2.4. Invalid
 *     Endpoint</a>
 */
public class OAuthRequestValidator {

  OAuthRequestParameters oAuthRequestParameters;

  public OAuthRequestValidator(OAuthRequestParameters oAuthRequestParameters) {
    this.oAuthRequestParameters = oAuthRequestParameters;
  }

  public void validate() {
    throwExceptionIfNotContainsClientId();
    throwExceptionIfDuplicateValue();
  }

  void throwExceptionIfNotContainsClientId() {
    if (!oAuthRequestParameters.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request", "authorization request must contains client_id");
    }
  }

  /**
   * 3.1. Authorization Endpoint validation
   *
   * <p>Request and response parameters MUST NOT be included more than once.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-3.1">3.1. Authorization
   *     Endpoint</a>
   */
  void throwExceptionIfDuplicateValue() {
    List<String> keys = oAuthRequestParameters.multiValueKeys();
    List<String> filteredKeys = keys.stream().filter(key -> !key.equals("resource")).toList();
    if (!filteredKeys.isEmpty()) {
      String keysValue = String.join(" ", filteredKeys);
      throw new OAuthBadRequestException(
          "invalid_request",
          String.format(
              "authorization request must not contains duplicate value; keys (%s)", keysValue));
    }
  }
}
