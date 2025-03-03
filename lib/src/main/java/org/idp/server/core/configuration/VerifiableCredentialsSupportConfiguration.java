package org.idp.server.core.configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

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
