package org.idp.server.core.extension.ciba.validator;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.platform.json.JsonNodeWrapper;

public interface CibaRequestValidator {

  default void throwExceptionIfInvalidRequestedExpiry(String value) {
    if (!value.matches("^[1-9][0-9]*$")) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "requested_expiry must be a positive integer string.");
    }

    try {
      Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_request", "requested_expiry is out of range for integer.");
    }
  }

  /**
   * RFC 9396 Section 2 & Section 5 - Authorization Details Validation
   *
   * <p>RFC 9396 Section 2 states: "The request parameter authorization_details contains, in JSON
   * notation, an array of objects."
   *
   * <p>RFC 9396 Section 5 states: "The AS MUST abort processing and respond with an error
   * invalid_authorization_details to the client if any of the following are true: - is missing
   * required fields for the authorization details type"
   *
   * <p>This method validates:
   *
   * <ul>
   *   <li>authorization_details MUST be a valid JSON array
   *   <li>authorization_details array MUST NOT be empty
   *   <li>Each element MUST contain required 'type' field
   * </ul>
   *
   * @throws BackchannelAuthenticationBadRequestException with error code
   *     "invalid_authorization_details"
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-2">RFC 9396 Section 2</a>
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9396#section-5">RFC 9396 Section 5</a>
   */
  default void throwExceptionIfInvalidAuthorizationDetails(String object) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromString(object);

      throwExceptionIfInvalidAuthorizationDetails(jsonNodeWrapper);
    } catch (Exception e) {
      if (e instanceof BackchannelAuthenticationBadRequestException) {
        throw (BackchannelAuthenticationBadRequestException) e;
      }
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_authorization_details", "authorization_details is invalid.");
    }
  }

  default void throwExceptionIfInvalidAuthorizationDetails(Object object) {
    try {
      JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(object);

      throwExceptionIfInvalidAuthorizationDetails(jsonNodeWrapper);
    } catch (Exception e) {
      if (e instanceof BackchannelAuthenticationBadRequestException) {
        throw (BackchannelAuthenticationBadRequestException) e;
      }
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_authorization_details", "authorization_details is invalid.");
    }
  }

  private void throwExceptionIfInvalidAuthorizationDetails(JsonNodeWrapper jsonNodeWrapper) {
    if (!jsonNodeWrapper.isArray()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_authorization_details", "authorization_details is not array.");
    }
    List<Map<String, Object>> listAsMap = jsonNodeWrapper.toListAsMap();
    if (listAsMap.isEmpty()) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_authorization_details", "authorization_detail object is unspecified.");
    }
    listAsMap.forEach(
        map -> {
          if (!map.containsKey("type")) {
            throw new BackchannelAuthenticationBadRequestException(
                "invalid_authorization_details",
                "type is required. authorization_detail object is missing 'type'.");
          }
        });
  }
}
