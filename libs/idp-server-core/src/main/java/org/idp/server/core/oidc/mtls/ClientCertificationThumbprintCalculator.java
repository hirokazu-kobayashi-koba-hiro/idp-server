/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.oidc.mtls;

import org.idp.server.basic.base64.Base64Codeable;
import org.idp.server.basic.hash.MessageDigestable;

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
public class ClientCertificationThumbprintCalculator implements Base64Codeable, MessageDigestable {

  ClientCertification clientCertification;

  public ClientCertificationThumbprintCalculator(ClientCertification clientCertification) {
    this.clientCertification = clientCertification;
  }

  public ClientCertificationThumbprint calculate() {
    byte[] der = clientCertification.der();
    byte[] bytes = digestWithSha256(der);
    return new ClientCertificationThumbprint(encodeWithUrlSafe(bytes));
  }
}
