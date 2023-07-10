package org.idp.server.verifiablecredential;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CredentialBuilder {
  Map<String, Object> values;

  public CredentialBuilder() {
    this.values = new HashMap<>();
  }

  public CredentialBuilder context(List<String> context) {
    values.put("@context", context);
    return this;
  }

  public CredentialBuilder id(String id) {
    values.put("id", id);
    return this;
  }

  public CredentialBuilder type(List<String> type) {
    values.put("type", type);
    return this;
  }

  public CredentialBuilder issuer(String issuer) {
    values.put("issuer", issuer);
    return this;
  }

  public CredentialBuilder issuanceDate(LocalDateTime issuanceDate) {
    values.put("issuanceDate", issuanceDate);
    return this;
  }

  public CredentialBuilder credentialSubject(Map<String, Object> credentialSubject) {
    values.put("credentialSubject", credentialSubject);
    return this;
  }

  public CredentialBuilder proof(Map<String, Object> proof) {
    values.put("proof", proof);
    return this;
  }

  public VerifiableCredential build() {
    return new VerifiableCredential(values);
  }
}
