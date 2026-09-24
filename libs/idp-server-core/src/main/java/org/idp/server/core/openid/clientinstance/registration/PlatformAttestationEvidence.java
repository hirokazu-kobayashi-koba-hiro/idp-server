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

package org.idp.server.core.openid.clientinstance.registration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.x509.X509CertificateChain;

/**
 * What a platform attestation verifier established about a registered Client Instance, kept on the
 * instance as {@code attestation_evidence}.
 *
 * <p>Kept for three uses, and shaped by them:
 *
 * <ol>
 *   <li><b>Revoking in bulk.</b> When an attestation key or an intermediate is found compromised,
 *       the instances whose chain passed through it have to be found. Each certificate of the
 *       presented chain is kept by serial number (lowercase hex, the key of Google's attestation
 *       status list), expiry and SHA-256
 *   <li><b>Auditing</b> what was established at registration: the key's security level and origin,
 *       the application identity
 *   <li><b>Re-evaluating</b> registered instances when the policy tightens, for example when
 *       StrongBox becomes required
 * </ol>
 *
 * <p>Deliberately not kept: the chain itself (serials and expiries are enough to match a revocation
 * list), and anything that identifies the device rather than the key.
 */
public class PlatformAttestationEvidence {

  String platform;
  Map<String, Object> key;
  Map<String, Object> app;
  List<Map<String, Object>> certificates;
  boolean bindingOnly;

  PlatformAttestationEvidence(
      String platform,
      Map<String, Object> key,
      Map<String, Object> app,
      List<Map<String, Object>> certificates,
      boolean bindingOnly) {
    this.platform = platform;
    this.key = key;
    this.app = app;
    this.certificates = certificates;
    this.bindingOnly = bindingOnly;
  }

  /**
   * Evidence of a verified platform attestation.
   *
   * @param key what the attestation established about the key (security level, origin)
   * @param app the application identity the attestation matched
   * @param chain the certificate chain as presented, leaf first
   */
  public static PlatformAttestationEvidence of(
      String platform,
      Map<String, Object> key,
      Map<String, Object> app,
      X509CertificateChain chain) {
    return new PlatformAttestationEvidence(
        platform, Map.copyOf(key), Map.copyOf(app), certificatesOf(chain), false);
  }

  /**
   * Evidence of a verifier that establishes the request hash binding only, and nothing about the
   * application or the device. Recorded as such, so that an instance registered this way can be
   * told apart from an attested one.
   */
  public static PlatformAttestationEvidence bindingOnly(String platform) {
    return new PlatformAttestationEvidence(platform, Map.of(), Map.of(), List.of(), true);
  }

  public String platform() {
    return platform;
  }

  public boolean isBindingOnly() {
    return bindingOnly;
  }

  public List<Map<String, Object>> certificates() {
    return certificates;
  }

  /** The representation stored on the instance. */
  public Map<String, Object> toMap(LocalDateTime verifiedAt) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("platform", platform);
    map.put("verified_at", verifiedAt.toString());
    if (bindingOnly) {
      map.put("binding_only", true);
      return map;
    }
    if (!key.isEmpty()) map.put("key", key);
    if (!app.isEmpty()) map.put("app", app);
    map.put("chain", Map.of("certificates", certificates));
    return map;
  }

  private static List<Map<String, Object>> certificatesOf(X509CertificateChain chain) {
    List<Map<String, Object>> certificates = new ArrayList<>();
    for (X509Certificate certificate : chain.certificates()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("serial", certificate.getSerialNumber().toString(16));
      entry.put("not_after", certificate.getNotAfter().toInstant().toString());
      entry.put("sha256", sha256(certificate));
      certificates.add(entry);
    }
    return List.copyOf(certificates);
  }

  private static String sha256(X509Certificate certificate) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(certificate.getEncoded());
      return HexFormat.of().formatHex(digest);
    } catch (CertificateEncodingException e) {
      throw new PlatformAttestationVerificationException(
          "failed to encode an attested certificate: " + e.getMessage(), e);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
