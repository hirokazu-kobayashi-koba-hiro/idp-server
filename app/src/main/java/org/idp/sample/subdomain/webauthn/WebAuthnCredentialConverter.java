package org.idp.sample.subdomain.webauthn;

import com.webauthn4j.converter.AttestedCredentialDataConverter;
import com.webauthn4j.converter.util.ObjectConverter;
import com.webauthn4j.credential.CredentialRecordImpl;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement;
import com.webauthn4j.data.extension.authenticator.AuthenticationExtensionsAuthenticatorOutputs;

public class WebAuthnCredentialConverter {

  WebAuthnCredential credential;
  AttestedCredentialDataConverter attestedCredentialDataConverter;

  public WebAuthnCredentialConverter(WebAuthnCredential credential) {
    this.credential = credential;
    ObjectConverter objectConverter = new ObjectConverter();
    this.attestedCredentialDataConverter =
        new AttestedCredentialDataConverter(objectConverter);
  }

  public CredentialRecordImpl convert() {
    AttestedCredentialData deserializedAttestedCredentialData =
        attestedCredentialDataConverter.convert(credential.attestationObject());

    //TODO
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
