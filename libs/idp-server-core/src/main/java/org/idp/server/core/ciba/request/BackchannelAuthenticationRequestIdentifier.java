package org.idp.server.core.ciba.request;

import java.util.Objects;
import org.idp.server.core.authentication.AuthorizationIdentifier;

public class BackchannelAuthenticationRequestIdentifier {
  String value;

  public BackchannelAuthenticationRequestIdentifier() {}

  public BackchannelAuthenticationRequestIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    BackchannelAuthenticationRequestIdentifier that = (BackchannelAuthenticationRequestIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  public AuthorizationIdentifier toAuthorizationIdentifier() {
    return new AuthorizationIdentifier(value);
  }
}
