package org.idp.server.core.oauth.mtls;

import java.util.Objects;
import org.idp.server.core.basic.base64.Base64Codeable;
import org.idp.server.core.basic.hash.MessageDigestable;

/**
 * certificate-thumbprint
 *
 * <p>The value of the x5t#S256 member is a base64url-encoded [RFC4648] SHA-256 [SHS] hash (a.k.a.,
 * thumbprint, fingerprint, or digest) of the DER encoding [X690] of the X.509 certificate
 * [RFC5280]. The base64url-encoded value MUST omit all trailing pad '=' characters and MUST NOT
 * include any line breaks, whitespace, or other additional characters.
 *
 * @see <a
 *     href="https://datatracker.ietf.org/doc/html/rfc8705#name-jwt-certificate-thumbprint-">thumbprint</a>
 */
public class ClientCertificationThumbprint implements Base64Codeable, MessageDigestable {

  String value;

  public ClientCertificationThumbprint() {}

  public ClientCertificationThumbprint(String value) {
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
    ClientCertificationThumbprint that = (ClientCertificationThumbprint) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }
}
