/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.vc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.idp.server.basic.json.JsonReadable;

public class VerifiableCredentialsSupportConfiguration implements JsonReadable {
  String format;
  String id;
  String scope;
  List<String> cryptographicBindingMethodsSupported = new ArrayList<>();
  List<String> cryptographicSuitesSupported = new ArrayList<>();
  VerifiableCredentialDefinitionConfiguration credentialDefinition;
  List<String> proofTypesSupported = new ArrayList<>();

  public String format() {
    return format;
  }

  public boolean hasFormat() {
    return Objects.nonNull(format) && !format.isEmpty();
  }

  public String id() {
    return id;
  }

  public boolean hasId() {
    return Objects.nonNull(id) && !id.isEmpty();
  }

  public String scope() {
    return scope;
  }

  public boolean hasScope() {
    return Objects.nonNull(scope) && !scope.isEmpty();
  }

  public List<String> cryptographicBindingMethodsSupported() {
    return cryptographicBindingMethodsSupported;
  }

  public boolean hasCryptographicBindingMethodsSupported() {
    return !cryptographicBindingMethodsSupported.isEmpty();
  }

  public boolean isSupportedCryptographicBindingMethod(String cryptographicBindingMethod) {
    return cryptographicBindingMethodsSupported.contains(cryptographicBindingMethod);
  }

  public List<String> cryptographicSuitesSupported() {
    return cryptographicSuitesSupported;
  }

  public boolean hasCryptographicSuitesSupported() {
    return !cryptographicSuitesSupported.isEmpty();
  }

  public boolean isSupportedCryptographicSuite(String cryptographicSuite) {
    return cryptographicSuitesSupported.contains(cryptographicSuite);
  }

  public VerifiableCredentialDefinitionConfiguration credentialDefinition() {
    return credentialDefinition;
  }

  public List<String> proofTypesSupported() {
    return proofTypesSupported;
  }

  public boolean hasProofTypesSupported() {
    return !proofTypesSupported.isEmpty();
  }

  public boolean isSupportedProofType(String proofType) {
    return proofTypesSupported.contains(proofType);
  }
}
