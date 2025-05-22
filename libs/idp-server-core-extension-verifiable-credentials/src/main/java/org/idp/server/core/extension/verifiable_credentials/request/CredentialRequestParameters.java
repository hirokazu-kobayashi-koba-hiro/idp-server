/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials.request;

import java.util.Map;
import org.idp.server.basic.type.OAuthRequestKey;
import org.idp.server.basic.type.verifiablecredential.CredentialDefinitionEntity;
import org.idp.server.basic.type.verifiablecredential.Format;
import org.idp.server.basic.type.verifiablecredential.ProofEntity;

public class CredentialRequestParameters implements VerifiableCredentialRequestTransformable {
  Map<String, Object> values;

  public CredentialRequestParameters() {
    this.values = Map.of();
  }

  public CredentialRequestParameters(Map<String, Object> values) {
    this.values = values;
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  public Format format() {
    return Format.of(getValueOrEmpty(OAuthRequestKey.format));
  }

  public CredentialDefinitionEntity credentialDefinitionEntity() {
    return new CredentialDefinitionEntity(values.get(OAuthRequestKey.credential_definition.name()));
  }

  public ProofEntity proofEntity() {
    return new ProofEntity(values.get(OAuthRequestKey.proof.name()));
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return (String) values.getOrDefault(key.name(), "");
  }

  public boolean contains(OAuthRequestKey key) {
    return values.containsKey(key.name());
  }

  public boolean isDefined() {
    return contains(OAuthRequestKey.format) && format().isDefined();
  }

  public boolean hasProof() {
    return contains(OAuthRequestKey.proof);
  }

  public Map<String, Object> values() {
    return values;
  }
}
