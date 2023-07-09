package org.idp.server.verifiablecredential;

import java.util.Map;
import org.idp.server.basic.json.JsonParser;
import org.idp.server.type.verifiablecredential.CNonce;
import org.idp.server.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.type.verifiablecredential.Format;

public class VerifiableCredentialResponseBuilder {
  Format format;
  VerifiableCredentialJwt credentialJwt;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  Map<String, Object> values;
  JsonParser jsonParser = JsonParser.createWithSnakeCaseStrategy();

  public VerifiableCredentialResponseBuilder() {}

  public VerifiableCredentialResponseBuilder add(Format format) {
    this.format = format;
    return this;
  }

  public VerifiableCredentialResponseBuilder add(VerifiableCredentialJwt credentialJwt) {
    this.credentialJwt = credentialJwt;
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    return this;
  }

  public VerifiableCredentialResponse build() {
    String contents = jsonParser.write(values);
    return new VerifiableCredentialResponse(
        format, credentialJwt, cNonce, cNonceExpiresIn, contents);
  }
}
