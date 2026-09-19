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

package org.idp.server.core.openid.extension.attestation.ios;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.cbor.CborInvalidException;
import org.idp.server.platform.cbor.CborValue;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * The CBOR attestation object {@code DCAppAttestService} produces, as presented at registration:
 *
 * <pre>
 * "platform_evidence": {
 *   "platform": "ios-app-attest",
 *   "attestation_object": "&lt;base64 CBOR&gt;"
 * }
 * </pre>
 *
 * <p>Decoded, it holds the format name, the certificate chain, a receipt, and the authenticator
 * data:
 *
 * <pre>
 * { fmt: 'apple-appattest',
 *   attStmt: { x5c: [ credCert, caCert ], receipt: ... },
 *   authData: ... }
 * </pre>
 */
public class IosAppAttestObject {

  static final String EVIDENCE_KEY = "attestation_object";
  static final String EXPECTED_FORMAT = "apple-appattest";

  String format;
  X509CertificateChain certificateChain;
  IosAppAttestAuthenticatorData authenticatorData;

  IosAppAttestObject(
      String format,
      X509CertificateChain certificateChain,
      IosAppAttestAuthenticatorData authenticatorData) {
    this.format = format;
    this.certificateChain = certificateChain;
    this.authenticatorData = authenticatorData;
  }

  public static IosAppAttestObject parse(Map<String, Object> evidence) {
    Object encoded = evidence.get(EVIDENCE_KEY);
    if (!(encoded instanceof String base64) || base64.isEmpty()) {
      throw new IosAppAttestException(
          "platform_evidence." + EVIDENCE_KEY + " must be a base64 encoded attestation object");
    }

    try {
      CborValue object = CborValue.parse(decode(base64));

      String format = object.get("fmt").textString();
      byte[] authData = object.get("authData").byteString();

      List<CborValue> x5c = object.get("attStmt").get("x5c").elements();
      if (x5c.isEmpty()) {
        throw new IosAppAttestException("attStmt.x5c is empty");
      }

      List<String> encodedChain = new java.util.ArrayList<>();
      for (CborValue certificate : x5c) {
        encodedChain.add(Base64.getEncoder().encodeToString(certificate.byteString()));
      }

      return new IosAppAttestObject(
          format,
          X509CertificateChain.parse(encodedChain),
          IosAppAttestAuthenticatorData.parse(authData));
    } catch (CborInvalidException e) {
      throw new IosAppAttestException(
          "platform_evidence."
              + EVIDENCE_KEY
              + " is not a valid attestation object: "
              + e.getMessage(),
          e);
    } catch (X509CertInvalidException e) {
      throw new IosAppAttestException(
          "attStmt.x5c is not a certificate chain: " + e.getMessage(), e);
    }
  }

  /**
   * Decodes base64, accepting the URL alphabet as well.
   *
   * <p>Apple hands the app raw bytes and leaves the encoding to it, so both alphabets turn up in
   * practice. Accepting either changes which characters map to which bits, never which bytes the
   * checks then run against, so it does not widen what is accepted.
   */
  private static byte[] decode(String base64) {
    String normalized = base64.replaceAll("\\s", "").replace('-', '+').replace('_', '/');
    int padding = (4 - normalized.length() % 4) % 4;
    return Base64.getDecoder().decode(normalized + "=".repeat(padding));
  }

  public String format() {
    return format;
  }

  public boolean hasExpectedFormat() {
    return EXPECTED_FORMAT.equals(format);
  }

  public X509CertificateChain certificateChain() {
    return certificateChain;
  }

  public IosAppAttestAuthenticatorData authenticatorData() {
    return authenticatorData;
  }
}
