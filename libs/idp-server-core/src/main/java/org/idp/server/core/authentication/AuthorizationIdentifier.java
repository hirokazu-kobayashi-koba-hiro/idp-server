package org.idp.server.core.authentication;

import java.util.Objects;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oidc.request.AuthorizationRequestIdentifier;

public class AuthorizationIdentifier {
  String value;

  public AuthorizationIdentifier() {}

  public AuthorizationIdentifier(AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    this.value = authorizationRequestIdentifier.value();
  }

  public AuthorizationIdentifier(BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    this.value = backchannelAuthenticationRequestIdentifier.value();
  }

  public AuthorizationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    AuthorizationIdentifier that = (AuthorizationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
