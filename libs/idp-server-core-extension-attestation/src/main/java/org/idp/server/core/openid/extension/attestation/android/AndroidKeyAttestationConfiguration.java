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

package org.idp.server.core.openid.extension.attestation.android;

import java.util.List;
import java.util.Map;

/**
 * What a client accepts as an Android key attestation.
 *
 * <p>Held on the client because a client is one application: the package name and the signing
 * certificate digests are properties of that app, and they are what turn "a genuine Android device"
 * into "this client's app on a genuine Android device".
 *
 * <pre>
 * "client_instance_platform_config": {
 *   "android_key_attestation": {
 *     "package_names": ["com.example.wallet"],
 *     "signature_digests": ["V2h5IGFyZSB5b3UgcmVhZGluZyB0aGlz"],
 *     "min_security_level": "trusted_environment",
 *     "trusted_root_certificates": []
 *   }
 * }
 * </pre>
 *
 * <p>{@code trusted_root_certificates} overrides the Google hardware attestation root. It exists so
 * that a chain can be produced without a device — a deployment that sets it is trusting whoever
 * holds that root, which is why the verifier logs a warning when it is used.
 */
public class AndroidKeyAttestationConfiguration {

  static final String ANDROID_KEY = "android_key_attestation";

  List<String> packageNames;
  List<String> signatureDigests;
  AndroidKeyAttestationSecurityLevel minSecurityLevel;
  List<String> trustedRootCertificates;

  AndroidKeyAttestationConfiguration(
      List<String> packageNames,
      List<String> signatureDigests,
      AndroidKeyAttestationSecurityLevel minSecurityLevel,
      List<String> trustedRootCertificates) {
    this.packageNames = packageNames;
    this.signatureDigests = signatureDigests;
    this.minSecurityLevel = minSecurityLevel;
    this.trustedRootCertificates = trustedRootCertificates;
  }

  /**
   * @param platformConfig the value of the client's {@code client_instance_platform_config}
   */
  @SuppressWarnings("unchecked")
  public static AndroidKeyAttestationConfiguration fromPlatformConfig(
      Map<String, Object> platformConfig) {

    Object androidConfig = platformConfig.get(ANDROID_KEY);
    if (!(androidConfig instanceof Map<?, ?> androidConfigMap)) {
      throw new AndroidKeyAttestationException(
          "client_instance_platform_config." + ANDROID_KEY + " is not configured for this client");
    }

    Map<String, Object> values = (Map<String, Object>) androidConfigMap;

    List<String> packageNames = stringList(values.get("package_names"));
    if (packageNames.isEmpty()) {
      throw new AndroidKeyAttestationException("package_names must not be empty");
    }

    List<String> signatureDigests = stringList(values.get("signature_digests"));
    if (signatureDigests.isEmpty()) {
      // Without the digests the package name alone decides, and a package name is not a secret:
      // any app can declare it on a device the attacker controls.
      throw new AndroidKeyAttestationException("signature_digests must not be empty");
    }

    AndroidKeyAttestationSecurityLevel minSecurityLevel =
        minSecurityLevel(values.get("min_security_level"));

    return new AndroidKeyAttestationConfiguration(
        packageNames,
        signatureDigests,
        minSecurityLevel,
        stringList(values.get("trusted_root_certificates")));
  }

  private static AndroidKeyAttestationSecurityLevel minSecurityLevel(Object value) {
    if (!(value instanceof String name) || name.isEmpty()) {
      return AndroidKeyAttestationSecurityLevel.trusted_environment;
    }
    for (AndroidKeyAttestationSecurityLevel level : AndroidKeyAttestationSecurityLevel.values()) {
      if (level.name().equals(name)) {
        return level;
      }
    }
    throw new AndroidKeyAttestationException("unknown min_security_level: " + name);
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  public List<String> packageNames() {
    return packageNames;
  }

  public List<String> signatureDigests() {
    return signatureDigests;
  }

  public AndroidKeyAttestationSecurityLevel minSecurityLevel() {
    return minSecurityLevel;
  }

  public List<String> trustedRootCertificates() {
    return trustedRootCertificates;
  }

  public boolean hasTrustedRootCertificates() {
    return !trustedRootCertificates.isEmpty();
  }

  /** True when the presented level is at least as strong as the configured minimum. */
  public boolean accepts(AndroidKeyAttestationSecurityLevel presented) {
    if (!presented.isBackedByHardware()) {
      return false;
    }
    return presented.value >= minSecurityLevel.value;
  }
}
