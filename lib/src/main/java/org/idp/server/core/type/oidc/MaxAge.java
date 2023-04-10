package org.idp.server.core.type.oidc;

import java.util.Objects;

/**
 * MaxAge
 *
 * <p>OPTIONAL. Maximum Authentication Age.
 *
 * <p>Specifies the allowable elapsed time in seconds since the last time the End-User was actively
 * authenticated by the OP. If the elapsed time is greater than this value, the OP MUST attempt to
 * actively re-authenticate the End-User.
 *
 * <p>(The max_age request parameter corresponds to the OpenID 2.0 PAPE [OpenID.PAPE] max_auth_age
 * request parameter.) When max_age is used, the ID Token returned MUST include an auth_time Claim
 * Value.
 */
public class MaxAge {
  String value;

  public MaxAge() {}

  public MaxAge(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public long toLongValue() {
    return Long.parseLong(value);
  }

  public boolean isValid() {
    if (!exists()) {
      return true;
    }
    try {
      long longValue = toLongValue();
      return longValue > 0;
    } catch (Exception e) {
      return false;
    }
  }
}
