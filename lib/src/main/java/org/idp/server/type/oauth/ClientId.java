package org.idp.server.type.oauth;

import java.util.Objects;

/**
 * ClientIdentifier
 *
 * <p>The authorization server issues the registered client a client identifier -- a unique string
 * representing the registration information provided by the client. The client identifier is not a
 * secret; it is exposed to the resource owner and MUST NOT be used alone for client authentication.
 * The client identifier is unique to the authorization server.
 *
 * <p>The client identifier string size is left undefined by this specification. The client should
 * avoid making assumptions about the identifier size. The authorization server SHOULD document the
 * size of any identifier it issues.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc6749#section-2.2">2.2. Client Identifier</a>
 */
public class ClientId {
  String value;

  public ClientId() {}

  public ClientId(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClientId clientId = (ClientId) o;
    return Objects.equals(value, clientId.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
