package org.idp.server.core.authentication;

import java.util.Objects;
import org.idp.server.core.ciba.request.BackchannelAuthenticationRequestIdentifier;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;

public class AuthenticationTransactionIdentifier {
  String value;

  public AuthenticationTransactionIdentifier() {}

  public AuthenticationTransactionIdentifier(
      AuthorizationRequestIdentifier authorizationRequestIdentifier) {
    this.value = authorizationRequestIdentifier.value();
  }

  public AuthenticationTransactionIdentifier(
      BackchannelAuthenticationRequestIdentifier backchannelAuthenticationRequestIdentifier) {
    this.value = backchannelAuthenticationRequestIdentifier.value();
  }

  public AuthenticationTransactionIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthenticationTransactionIdentifier that = (AuthenticationTransactionIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
