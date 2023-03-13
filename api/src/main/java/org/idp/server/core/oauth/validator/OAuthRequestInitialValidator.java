package org.idp.server.core.oauth.validator;

import java.util.List;
import org.idp.server.core.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.type.OAuthRequestParameters;

/** OAuthRequestInitialValidator */
public class OAuthRequestInitialValidator {

  public void validate(OAuthRequestParameters oAuthRequestParameters) {
    throwIfNotContainsClientId(oAuthRequestParameters);
    throwIfDuplicateValue(oAuthRequestParameters);
  }

  void throwIfNotContainsClientId(OAuthRequestParameters oAuthRequestParameters) {
    if (!oAuthRequestParameters.hasClientId()) {
      throw new OAuthBadRequestException("authorization request must contains client_id");
    }
  }

  void throwIfDuplicateValue(OAuthRequestParameters oAuthRequestParameters) {
    List<String> keys = oAuthRequestParameters.multiValueKeys();
    List<String> filteredKeys = keys.stream().filter(key -> !key.equals("resource")).toList();
    if (!filteredKeys.isEmpty()) {
      String keysValue = String.join(" ", filteredKeys);
      throw new OAuthBadRequestException(
          String.format(
              "authorization request must not contains duplicate value; keys (%s)", keysValue));
    }
  }
}
