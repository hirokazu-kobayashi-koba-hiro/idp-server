package org.idp.server.oauth.validator;

import java.util.List;
import org.idp.server.oauth.OAuthRequestParameters;
import org.idp.server.oauth.exception.OAuthBadRequestException;

/** OAuthRequestInitialValidator */
public class OAuthRequestValidator {

  OAuthRequestParameters oAuthRequestParameters;

  public OAuthRequestValidator(OAuthRequestParameters oAuthRequestParameters) {
    this.oAuthRequestParameters = oAuthRequestParameters;
  }

  public void validate() {
    throwIfNotContainsClientId();
    throwIfDuplicateValue();
  }

  void throwIfNotContainsClientId() {
    if (!oAuthRequestParameters.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request", "authorization request must contains client_id");
    }
  }

  void throwIfDuplicateValue() {
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
