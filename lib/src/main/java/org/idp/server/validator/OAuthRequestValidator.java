package org.idp.server.validator;

import java.util.List;
import org.idp.server.oauth.exception.OAuthBadRequestException;
import org.idp.server.type.OAuthRequestParameters;

/** OAuthRequestInitialValidator */
public class OAuthRequestValidator {

  public void validate(OAuthRequestParameters oAuthRequestParameters) {
    throwIfNotContainsClientId(oAuthRequestParameters);
    throwIfDuplicateValue(oAuthRequestParameters);
  }

  void throwIfNotContainsClientId(OAuthRequestParameters oAuthRequestParameters) {
    if (!oAuthRequestParameters.hasClientId()) {
      throw new OAuthBadRequestException(
          "invalid_request", "authorization request must contains client_id");
    }
  }

  void throwIfDuplicateValue(OAuthRequestParameters oAuthRequestParameters) {
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
