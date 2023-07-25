package org.idp.server.verifiablecredential;

import java.util.Map;
import org.idp.server.type.verifiablecredential.Format;

public class BatchVerifiableCredentialResponse {
  Format format;
  VerifiableCredentialJwt credentialJwt;

  public BatchVerifiableCredentialResponse() {}

  public BatchVerifiableCredentialResponse(Format format, VerifiableCredentialJwt credentialJwt) {
    this.format = format;
    this.credentialJwt = credentialJwt;
  }

  public Format getFormat() {
    return format;
  }

  public VerifiableCredentialJwt credentialJwt() {
    return credentialJwt;
  }

  public Map<String, Object> toMap() {
    return Map.of("format", format.name(), "credential", credentialJwt.value());
  }
}
