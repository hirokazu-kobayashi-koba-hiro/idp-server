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

package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.RegistrationParameters;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.server.ServerProperty;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.idp.server.platform.json.JsonReadable;

public class WebAuthn4jConfiguration implements JsonReadable {
  String rpId;
  String rpName;
  @Deprecated String origin;
  List<String> allowedOrigins;
  byte[] tokenBindingId;
  String attestationPreference;
  String authenticatorAttachment;
  String residentKey;

  boolean requireResidentKey;
  boolean userVerificationRequired;
  boolean userPresenceRequired;

  List<Integer> pubKeyCredAlgorithms;
  Long timeout;

  public WebAuthn4jConfiguration() {}

  public WebAuthn4jConfiguration(
      String rpId,
      String rpName,
      String origin,
      byte[] tokenBindingId,
      String attestationPreference,
      String authenticatorAttachment,
      boolean requireResidentKey,
      boolean userVerificationRequired,
      boolean userPresenceRequired) {
    this.rpId = rpId;
    this.rpName = rpName;
    this.origin = origin;
    this.tokenBindingId = tokenBindingId;
    this.attestationPreference = attestationPreference;
    this.authenticatorAttachment = authenticatorAttachment;
    this.requireResidentKey = requireResidentKey;
    this.userVerificationRequired = userVerificationRequired;
    this.userPresenceRequired = userPresenceRequired;
  }

  public List<String> getAllowedOrigins() {
    if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
      return allowedOrigins;
    }
    if (origin != null && !origin.isEmpty()) {
      return List.of(origin);
    }
    return List.of();
  }

  RegistrationParameters toRegistrationParameters(WebAuthn4jChallenge webAuthn4jChallenge) {

    Set<Origin> origins =
        getAllowedOrigins().stream().map(Origin::create).collect(Collectors.toSet());

    ServerProperty serverProperty =
        ServerProperty.builder().origins(origins).rpId(rpId).challenge(webAuthn4jChallenge).build();

    List<PublicKeyCredentialParameters> pubKeyCredParams = null;

    return new RegistrationParameters(
        serverProperty, pubKeyCredParams, userVerificationRequired, userPresenceRequired);
  }

  public String rpId() {
    return rpId;
  }

  public String rpName() {
    return rpName;
  }

  public String origin() {
    return origin;
  }

  public byte[] tokenBindingId() {
    return tokenBindingId;
  }

  public String attestationPreference() {
    return attestationPreference;
  }

  public String authenticatorAttachment() {
    return authenticatorAttachment;
  }

  public boolean requireResidentKey() {
    return requireResidentKey;
  }

  public boolean userVerificationRequired() {
    return userVerificationRequired;
  }

  public boolean userPresenceRequired() {
    return userPresenceRequired;
  }

  public String residentKey() {
    return residentKey;
  }

  public List<Integer> pubKeyCredAlgorithms() {
    return pubKeyCredAlgorithms;
  }

  public Long timeout() {
    return timeout;
  }
}
