/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.authenticators.webauthn4j;

import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;

public class WebAuthn4jCredentialConverter {

  WebAuthn4jCredential credential;
  AttestedCredentialDataConverter attestedCredentialDataConverter;

  public WebAuthn4jCredentialConverter(WebAuthn4jCredential credential) {
    this.credential = credential;
    ObjectConverter objectConverter = new ObjectConverter();
    this.attestedCredentialDataConverter = new AttestedCredentialDataConverter(objectConverter);
  }

  public CredentialRecordImpl convert() {
    AttestedCredentialData deserializedAttestedCredentialData =
        attestedCredentialDataConverter.convert(credential.attestationObject());

    // TODO
    return new CredentialRecordImpl(
        new NoneAttestationStatement(),
        null,
        null,
        null,
        credential.signCount(),
        deserializedAttestedCredentialData,
        new AuthenticationExtensionsAuthenticatorOutputs<>(),
        null,
        null,
        null);
  }
}
