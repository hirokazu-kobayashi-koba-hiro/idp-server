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

package org.idp.server.core.openid.identity.device.credential;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;

/**
 * JWT Bearer credential specific data.
 *
 * <p>Extracted from DeviceCredential.typeSpecificData() for JWT Bearer Grant (RFC 7523).
 */
public class JwtBearerCredentialData {

  private final String algorithm;
  private final String secretValue;
  private final String jwks;

  private JwtBearerCredentialData(String algorithm, String secretValue, String jwks) {
    this.algorithm = algorithm;
    this.secretValue = secretValue;
    this.jwks = jwks;
  }

  public static JwtBearerCredentialData from(Map<String, Object> data) {
    String algorithm = (String) data.get("algorithm");
    String secretValue = (String) data.get("secret_value");
    String jwks = extractJwks(data);
    return new JwtBearerCredentialData(algorithm, secretValue, jwks);
  }

  public static JwtBearerCredentialData symmetric(String algorithm, String secretValue) {
    return new JwtBearerCredentialData(algorithm, secretValue, null);
  }

  public static JwtBearerCredentialData asymmetric(String algorithm, String jwks) {
    return new JwtBearerCredentialData(algorithm, null, jwks);
  }

  private static String extractJwks(Map<String, Object> data) {
    Object jwksObj = data.get("jwks");
    if (jwksObj == null) {
      return null;
    }
    if (jwksObj instanceof String) {
      return (String) jwksObj;
    }
    JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
    return jsonConverter.write(jwksObj);
  }

  public String algorithm() {
    return algorithm;
  }

  public boolean hasAlgorithm() {
    return algorithm != null && !algorithm.isEmpty();
  }

  public String secretValue() {
    return secretValue;
  }

  public boolean hasSecretValue() {
    return secretValue != null && !secretValue.isEmpty();
  }

  public String jwks() {
    return jwks;
  }

  public boolean hasJwks() {
    return jwks != null && !jwks.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    if (hasAlgorithm()) map.put("algorithm", algorithm);
    if (hasSecretValue()) map.put("secret_value", secretValue);
    if (hasJwks()) map.put("jwks", jwks);
    return map;
  }
}
