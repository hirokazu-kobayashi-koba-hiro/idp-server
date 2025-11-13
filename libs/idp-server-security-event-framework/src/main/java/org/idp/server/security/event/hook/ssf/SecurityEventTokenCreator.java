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

package org.idp.server.security.event.hook.ssf;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.date.SystemDateTime;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebSignatureFactory;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;
import org.idp.server.platform.security.SecurityEvent;

public class SecurityEventTokenCreator {

  SecurityEvent securityEvent;
  SharedSignalFrameworkMetadataConfig metadataConfig;
  SharedSignalFrameworkTransmissionConfig transmissionConfig;
  LoggerWrapper log = LoggerWrapper.getLogger(SecurityEventTokenCreator.class);

  public SecurityEventTokenCreator(
      SecurityEvent securityEvent,
      SharedSignalFrameworkMetadataConfig metadataConfig,
      SharedSignalFrameworkTransmissionConfig transmissionConfig) {
    this.securityEvent = securityEvent;
    this.metadataConfig = metadataConfig;
    this.transmissionConfig = transmissionConfig;
  }

  public SecurityEventToken create() {

    try {
      SecurityEventTokenEntity securityEventTokenEntity = convert();
      JsonWebSignatureFactory jsonWebSignatureFactory = new JsonWebSignatureFactory();

      Map<String, Object> claims = new HashMap<>();
      claims.put("iss", securityEventTokenEntity.issuerValue());
      claims.put("jti", UUID.randomUUID().toString());
      claims.put("iat", SystemDateTime.toEpochSecond(SystemDateTime.now()));
      // aud is RECOMMENDED (not REQUIRED) per RFC 8417 Section 2.2
      if (securityEventTokenEntity.hasAudience()) {
        claims.put("aud", securityEventTokenEntity.audience());
      }
      claims.put("events", securityEventTokenEntity.eventAsMap());

      Map<String, Object> headers = transmissionConfig.securityEventTokenHeaders();

      JsonWebSignature jsonWebSignature =
          jsonWebSignatureFactory.createWithAsymmetricKey(
              claims, headers, metadataConfig.jwks(), transmissionConfig.kid());
      String jws = jsonWebSignature.serialize();

      return new SecurityEventToken(jws);
    } catch (JsonWebKeyInvalidException | JoseInvalidException e) {

      log.error(e.getMessage(), e);

      throw new SecurityEventTokenCreationFailedException(
          "security event token creation is failed.", e);
    }
  }

  public SecurityEventTokenEntity convert() {
    String setIssuer = metadataConfig.issuer();
    String audience = resolveAudience();
    SharedSecurityEvent sharedSecurityEvent = convertToSecurityEvent();

    return new SecurityEventTokenEntity(setIssuer, audience, sharedSecurityEvent);
  }

  /**
   * Resolves the audience (aud) value for the SET.
   *
   * <p>Priority:
   *
   * <ol>
   *   <li>Stream Configuration's aud (if configured)
   *   <li>Fallback to client ID from security event
   * </ol>
   *
   * @return the audience value
   */
  private String resolveAudience() {
    // Priority 1: Use stream configuration's aud if available
    if (metadataConfig.hasStreamConfiguration()) {
      StreamConfiguration streamConfig = metadataConfig.streamConfiguration();
      if (streamConfig.hasAud()) {
        return streamConfig.audValue();
      }
    }

    // Fallback. Payload aud is not set.
    return "";
  }

  private SharedSecurityEvent convertToSecurityEvent() {
    SecurityEventTypeIdentifier typeIdentifier = transmissionConfig.securityEventTypeIdentifier();

    Map<String, String> subjectMap =
        Map.of("sub", securityEvent.userSub(), "iss", securityEvent.tokenIssuerValue());
    SecurityEventSubjectPayload securityEventSubjectPayload =
        new SecurityEventSubjectPayload(subjectMap);
    SecurityEventSubject subject =
        new SecurityEventSubject(SecuritySubjectFormat.iss_sub, securityEventSubjectPayload);

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(securityEvent.toMap());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> payload =
        MappingRuleObjectMapper.execute(
            transmissionConfig.securityEventTokenAdditionalPayloadMappingRules(), jsonPathWrapper);
    SecurityEventPayload eventPayload = new SecurityEventPayload(payload);

    return new SharedSecurityEvent(typeIdentifier, subject, eventPayload);
  }
}
