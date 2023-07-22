package org.idp.server.verifiablecredential.verifier;

import org.idp.server.basic.jose.JoseInvalidException;
import org.idp.server.basic.jose.JoseType;
import org.idp.server.basic.jose.JsonWebSignature;
import org.idp.server.basic.jose.JsonWebSignatureHeader;
import org.idp.server.configuration.VerifiableCredentialConfiguration;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;

public class VerifiableCredentialJwtProofVerifier {
  String jwt;
  VerifiableCredentialConfiguration configuration;

  public VerifiableCredentialJwtProofVerifier(
      String jwt, VerifiableCredentialConfiguration configuration) {
    this.jwt = jwt;
    this.configuration = configuration;
  }

  public void verify() {
    JoseType joseType = parseJoseType(jwt);
    //header
    throwExceptionIfSignatureAlgIsNone(joseType);
    JsonWebSignature jsonWebSignature = parse(jwt);
    JsonWebSignatureHeader header = jsonWebSignature.header();
    throwExceptionIfNotContainsAlg(header);
    throwExceptionIfNotContainsType(header);
    throwExceptionIfNotContainsAnyKeyClaim(header);
    throwExceptionIfMultiKeyClaims(header);
    throwExceptionIfSignatureIsSymmetricAlg(jsonWebSignature);

    //payload

  }

  void throwExceptionIfSignatureAlgIsNone(JoseType joseType) {
    if (joseType.isPlain()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws alg must be not none alg");
    }
  }

  void throwExceptionIfNotContainsAlg(JsonWebSignatureHeader header) {
    if (!header.hasAlg()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws header must contains alg claim");
    }
  }

  void throwExceptionIfNotContainsAnyKeyClaim(JsonWebSignatureHeader header) {
    if (!header.hasKid() && !header.hasJwk() && !header.hasX5c()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws header must contains kid or jwk or x5c claim");
    }
  }

  void throwExceptionIfMultiKeyClaims(JsonWebSignatureHeader header) {
    if (header.hasKid() && (header.hasJwk() || header.hasX5c())
        || header.hasJwk() && (header.hasKid() || header.hasX5c())
        || header.hasJwk() && (header.hasKid() || header.hasJwk())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "proof of jws header must not contains multi key claim  kid, jwk, x5c");
    }
  }

  void throwExceptionIfNotContainsType(JsonWebSignatureHeader header) {
    if (!header.hasType()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws header must contains type claim");
    }
  }

  void throwExceptionIfSignatureIsSymmetricAlg(JsonWebSignature jws) {
    if (jws.isSymmetricType()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws alg must be not symmetric alg");
    }
  }

  JoseType parseJoseType(String jwt) {
    try {
      return JoseType.parse(jwt);
    } catch (JoseInvalidException e) {
      throw new VerifiableCredentialBadRequestException("invalid_request", e.getMessage());
    }
  }

  JsonWebSignature parse(String jwt) {
    try {
      return JsonWebSignature.parse(jwt);
    } catch (JoseInvalidException e) {
      throw new VerifiableCredentialBadRequestException("invalid_request", e.getMessage());
    }
  }
}
