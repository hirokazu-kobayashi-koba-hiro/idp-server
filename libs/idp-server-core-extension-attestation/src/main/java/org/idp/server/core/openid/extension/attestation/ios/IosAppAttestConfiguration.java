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

import java.util.List;
import java.util.Map;

/**
 * The client's App Attest configuration, read from {@code client_instance_platform_config}.
 *
 * <pre>
 * "ios_app_attest": {
 *   "app_ids": ["ABCDE12345.com.example.wallet"],
 *   "environment": "production",
 *   "trusted_root_certificates": []
 * }
 * </pre>
 */
public class IosAppAttestConfiguration {

  static final String IOS_KEY = "ios_app_attest";

  List<String> appIds;
  IosAppAttestEnvironment environment;
  List<String> trustedRootCertificates;

  IosAppAttestConfiguration(
      List<String> appIds,
      IosAppAttestEnvironment environment,
      List<String> trustedRootCertificates) {
    this.appIds = appIds;
    this.environment = environment;
    this.trustedRootCertificates = trustedRootCertificates;
  }

  /**
   * @param platformConfig the value of the client's {@code client_instance_platform_config}
   */
  @SuppressWarnings("unchecked")
  public static IosAppAttestConfiguration fromPlatformConfig(Map<String, Object> platformConfig) {

    Object iosConfig = platformConfig.get(IOS_KEY);
    if (!(iosConfig instanceof Map<?, ?> iosConfigMap)) {
      throw new IosAppAttestException(
          "client_instance_platform_config." + IOS_KEY + " is not configured for this client");
    }

    Map<String, Object> values = (Map<String, Object>) iosConfigMap;

    List<String> appIds = stringList(values.get("app_ids"));
    if (appIds.isEmpty()) {
      // The App ID is the only thing tying the attestation to this client's application. Apple
      // signs the attestation for any app on the device, so without it any App Attest capable app
      // would satisfy the remaining checks.
      throw new IosAppAttestException("app_ids must not be empty");
    }

    return new IosAppAttestConfiguration(
        appIds,
        environment(values.get("environment")),
        stringList(values.get("trusted_root_certificates")));
  }

  private static IosAppAttestEnvironment environment(Object value) {
    if (!(value instanceof String name) || name.isEmpty()) {
      return IosAppAttestEnvironment.production;
    }
    for (IosAppAttestEnvironment environment : IosAppAttestEnvironment.values()) {
      if (environment.name().equals(name)) {
        return environment;
      }
    }
    throw new IosAppAttestException("unknown environment: " + name);
  }

  private static List<String> stringList(Object value) {
    if (!(value instanceof List<?> list)) {
      return List.of();
    }
    return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
  }

  public List<String> appIds() {
    return appIds;
  }

  public IosAppAttestEnvironment environment() {
    return environment;
  }

  public List<String> trustedRootCertificates() {
    return trustedRootCertificates;
  }

  public boolean hasTrustedRootCertificates() {
    return !trustedRootCertificates.isEmpty();
  }
}
