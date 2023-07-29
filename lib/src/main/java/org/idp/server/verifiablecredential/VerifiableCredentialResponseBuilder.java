package org.idp.server.verifiablecredential;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.type.verifiablecredential.CNonce;
import org.idp.server.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.type.verifiablecredential.Format;
import org.idp.server.type.verifiablecredential.TransactionId;

public class VerifiableCredentialResponseBuilder {
  Format format;
  VerifiableCredentialJwt credentialJwt = new VerifiableCredentialJwt();
  CNonce cNonce = new CNonce();
  CNonceExpiresIn cNonceExpiresIn = new CNonceExpiresIn();
  TransactionId transactionId = new TransactionId();
  Map<String, Object> values = new HashMap<>();
  JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();

  public VerifiableCredentialResponseBuilder() {}

  public VerifiableCredentialResponseBuilder add(Format format) {
    this.format = format;
    values.put("format", format.name());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(VerifiableCredentialJwt credentialJwt) {
    this.credentialJwt = credentialJwt;
    values.put("credential", credentialJwt.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonce cNonce) {
    this.cNonce = cNonce;
    values.put("c_nonce", cNonce.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(CNonceExpiresIn cNonceExpiresIn) {
    this.cNonceExpiresIn = cNonceExpiresIn;
    values.put("c_nonce_expires_in", cNonceExpiresIn.value());
    return this;
  }

  public VerifiableCredentialResponseBuilder add(TransactionId transactionId) {
    this.transactionId = transactionId;
    values.put("transaction_id", transactionId.value());
    return this;
  }

  public VerifiableCredentialResponse build() {
    String contents = jsonConverter.write(values);
    return new VerifiableCredentialResponse(
        format, credentialJwt, cNonce, cNonceExpiresIn, contents);
  }
}
