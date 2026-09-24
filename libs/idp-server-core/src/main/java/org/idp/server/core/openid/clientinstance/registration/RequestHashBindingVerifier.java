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

import java.util.Map;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Verifies only the request hash binding of a registration.
 *
 * <pre>
 *   request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 * </pre>
 *
 * <p>This establishes two of the three bindings required by {@link PlatformAttestationVerifier}:
 * the evidence is tied to this challenge and to the key being registered. It establishes <b>nothing
 * about the application or the device</b> — there is no certificate chain, no hardware root and no
 * application identity check — so anyone able to reach the endpoint can register a key of their
 * choosing.
 *
 * <p><b>Not for production.</b> It exists so that the registration flow and the hash derivation
 * shared with the mobile clients can be exercised without a real device, and is only registered
 * when explicitly enabled.
 */
public class RequestHashBindingVerifier implements PlatformAttestationVerifier {

  public static final String PLATFORM = "request-hash-binding-development-only";

  LoggerWrapper log = LoggerWrapper.getLogger(RequestHashBindingVerifier.class);

  @Override
  public String platform() {
    return PLATFORM;
  }

  @Override
  public void verify(PlatformAttestationVerificationRequest request) {
    String presented = stringValue(request.evidence(), "request_hash");
    if (presented == null) {
      throw new PlatformAttestationVerificationException(
          "platform_evidence.request_hash is required");
    }

    ClientInstanceRequestHash expected = derive(request);

    if (!expected.matches(presented)) {
      throw new PlatformAttestationVerificationException(
          "platform_evidence.request_hash does not bind the challenge to the instance key");
    }

    log.warn(
        "Client instance registered with request hash binding only: tenant={}, client_id={}, instance_id={}."
            + " No application or device attestation was verified; this verifier must not be enabled in production.",
        request.tenant().identifierValue(),
        request.challenge().clientId(),
        request.challenge().instanceId());
  }

  private ClientInstanceRequestHash derive(PlatformAttestationVerificationRequest request) {
    try {
      return ClientInstanceRequestHash.derive(
          request.challenge().challenge(), request.instanceKey());
    } catch (ClientInstanceRegistrationException e) {
      throw new PlatformAttestationVerificationException(e.getMessage(), e);
    }
  }

  private String stringValue(Map<String, Object> evidence, String key) {
    Object value = evidence.get(key);
    return value instanceof String stringValue && !stringValue.isEmpty() ? stringValue : null;
  }
}
