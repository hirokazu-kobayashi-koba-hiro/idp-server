package org.idp.server.core.type.oidc;

import java.util.Objects;

/**
 * ResponseMode
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest">3.1.2.1.
 *     Authentication Request</a>
 * @see <a href="https://openid.net/specs/oauth-v2-multiple-response-types-1_0.html">OAuth 2.0
 *     Multiple Response Type Encoding Practices</a>
 * @see <a href="https://openid.net/specs/openid-financial-api-jarm.html">Financial-grade API: JWT
 *     Secured Authorization Response Mode for OAuth 2.0 (JARM)</a>
 */
public enum ResponseMode {
  query("query", "?"),
  fragment("fragment", "#"),
  form_post("form_post", ""),
  query_jwt("query.jwt", "?"),
  fragment_jwt("fragment.jwt", "#"),
  form_post_jwt("form_post.jwt", ""),
  jwt("jwt", ""),
  direct_post("direct_post", ""),
  undefined("", ""),
  unknown("", "");

  String value;
  String responseModeValue;

  ResponseMode(String value, String responseModeValue) {
    this.value = value;
    this.responseModeValue = responseModeValue;
  }

  public static ResponseMode of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ResponseMode responseMode : ResponseMode.values()) {
      if (responseMode.value.equals(value)) {
        return responseMode;
      }
    }
    return unknown;
  }

  public String responseModeValue() {
    return responseModeValue;
  }

  public boolean isDefinedResponseModeValue() {
    return !responseModeValue.isEmpty();
  }

  public boolean isDefined() {
    return this != undefined;
  }

  public boolean isJwtMode() {
    return this == query_jwt || this == fragment_jwt || this == jwt || this == form_post_jwt;
  }

  public boolean isJwt() {
    return this == jwt;
  }
}
