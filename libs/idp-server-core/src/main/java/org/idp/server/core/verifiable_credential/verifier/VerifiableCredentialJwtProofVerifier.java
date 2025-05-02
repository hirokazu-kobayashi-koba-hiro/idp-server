package org.idp.server.core.verifiable_credential.verifier;

import java.security.PublicKey;
import java.util.List;
import org.idp.server.basic.jose.*;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.VerifiableCredentialConfiguration;
import org.idp.server.core.token.OAuthToken;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.core.verifiable_credential.request.VerifiableCredentialRequestTransformable;

public class VerifiableCredentialJwtProofVerifier
    implements VerifiableCredentialRequestTransformable {
  String jwt;
  OAuthToken oAuthToken;
  VerifiableCredentialConfiguration configuration;
  ClientConfiguration clientConfiguration;

  public VerifiableCredentialJwtProofVerifier(
      String jwt,
      OAuthToken oAuthToken,
      VerifiableCredentialConfiguration configuration,
      ClientConfiguration clientConfiguration) {
    this.jwt = jwt;
    this.oAuthToken = oAuthToken;
    this.configuration = configuration;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    JoseType joseType = parseJoseType(jwt);
    // header
    throwExceptionIfSignatureAlgIsNone(joseType);
    JsonWebSignature jsonWebSignature = parse(jwt);
    JsonWebTokenClaims claims = jsonWebSignature.claims();
    JsonWebSignatureHeader header = jsonWebSignature.header();
    throwExceptionIfNotContainsAlg(header);
    throwExceptionIfInvalidType(header);
    throwExceptionIfNotContainsAnyKeyClaim(header);
    throwExceptionIfMultiKeyClaims(header);
    throwExceptionIfSignatureIsSymmetricAlg(jsonWebSignature);

    // payload
    throwExceptionIfInvalidIss(claims);
    throwExceptionIfInvalidAud(claims);
    throwExceptionIfInvalidIat(claims);
    throwExceptionIfInvalidNonce(claims);

    // signature
    throwExceptionIfInvalidSignature(jsonWebSignature);
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

  void throwExceptionIfInvalidType(JsonWebSignatureHeader header) {
    if (!header.hasType()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws header must contains type claim");
    }
    if (!header.type().equals("openid4vci-proof+jwt")) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "type claim must be openid4vci-proof+jwt");
    }
  }

  void throwExceptionIfSignatureIsSymmetricAlg(JsonWebSignature jws) {
    if (jws.isSymmetricType()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "proof of jws alg must be not symmetric alg");
    }
  }

  /**
   * iss: OPTIONAL (string).
   *
   * <p>The value of this claim MUST be the client_id of the client making the credential request.
   * This claim MUST be omitted if the access token authorizing the issuance call was obtained from
   * a Pre-Authorized Code Flow through anonymous access to the token endpoint.
   */
  void throwExceptionIfInvalidIss(JsonWebTokenClaims claims) {
    if (!claims.hasIss()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "iss claims does not contain, iss is required");
    }
    String iss = claims.getIss();
    if (!iss.equals(clientConfiguration.clientIdValue())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "iss claims must be client_id");
    }
  }

  /**
   * aud: REQUIRED (string).
   *
   * <p>The value of this claim MUST be the Credential Issuer Identifier.
   */
  void throwExceptionIfInvalidAud(JsonWebTokenClaims claims) {
    if (!claims.hasAud()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "aud claims does not contain, aud is required");
    }
    List<String> aud = claims.getAud();
    if (!aud.contains(configuration.credentialIssuer())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "aud must be the Credential Issuer Identifier.");
    }
  }

  /**
   * iat: REQUIRED (number).
   *
   * <p>The value of this claim MUST be the time at which the key proof was issued using the syntax
   * defined in [RFC7519].
   */
  void throwExceptionIfInvalidIat(JsonWebTokenClaims claims) {
    if (claims.hasIat()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "iat claims does not contains, iat is required");
    }
  }

  void throwExceptionIfInvalidNonce(JsonWebTokenClaims claims) {
    if (claims.contains("nonce")) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "nonce claims does not contains, nonce is required");
    }
    String cNonce = claims.getValue("nonce");
    if (!cNonce.equals(oAuthToken.cNonce().value())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "nonce claims does not match c_nonce, the value is a c_nonce provided by the credential issuer.");
    }
  }

  void throwExceptionIfInvalidSignature(JsonWebSignature jsonWebSignature)
      throws VerifiableCredentialBadRequestException {
    try {
      JsonWebSignatureHeader header = jsonWebSignature.header();
      PublicKey publicKey = transformPublicKey(header);
      JsonWebSignatureVerifier jsonWebSignatureVerifier =
          new JsonWebSignatureVerifier(header, publicKey);
      jsonWebSignatureVerifier.verify(jsonWebSignature);
    } catch (JoseInvalidException | VerifiableCredentialRequestInvalidException e) {
      throw new VerifiableCredentialBadRequestException("invalid_request", e.getMessage());
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
