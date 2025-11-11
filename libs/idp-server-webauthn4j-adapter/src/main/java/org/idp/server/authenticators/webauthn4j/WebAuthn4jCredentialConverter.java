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

import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.AuthenticatorTransport;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;
import com.webauthn4j.data.extension.authenticator.RegistrationExtensionAuthenticatorOutput;
import java.util.Base64;
import java.util.Set;
import java.util.stream.Collectors;

public class WebAuthn4jCredentialConverter {

  WebAuthn4jCredential credential;
  AttestedCredentialDataConverter attestedCredentialDataConverter;

  public WebAuthn4jCredentialConverter(WebAuthn4jCredential credential) {
    this.credential = credential;
    ObjectConverter objectConverter = new ObjectConverter();
    this.attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
  }

  public CredentialRecordImpl convert() {
    Base64.Decoder urlDecoder = Base64.getUrlDecoder();
    AttestedCredentialData deserializedAttestedCredentialData =
        attestedCredentialDataConverter.convert(
            urlDecoder.decode(credential.attestedCredentialData()));

    // Restore transports from database (important for UX - browser can choose optimal transport)
    Set<AuthenticatorTransport> transports =
        credential.transports() != null
            ? credential.transports().stream()
                .map(AuthenticatorTransport::create)
                .collect(Collectors.toSet())
            : null;

    // Note: We use NoneAttestationStatement because:
    // - We only store attestation_type (string) in DB, not the full attestation statement bytes
    // - Reconstructing original attestation statement requires attestation_object_bytes (not
    // stored)
    // - For authentication verification, only AttestedCredentialData and signCount are needed
    // - AttestationStatement is primarily used during registration, not authentication
    return new CredentialRecordImpl(
        new NoneAttestationStatement(), // attestationStatement
        null, // uvInitialized - not stored in DB
        null, // backupEligible - not stored in DB
        null, // backupState - not stored in DB
        credential.signCount(), // counter
        deserializedAttestedCredentialData, // attestedCredentialData
        new AuthenticationExtensionsAuthenticatorOutputs<
            RegistrationExtensionAuthenticatorOutput>(), // authenticatorExtensions
        null, // clientData - not stored in DB
        null, // clientExtensions - not stored in DB
        transports); // transports - restored from DB
  }
}
