package org.idp.server.verifiablecredential.verifier;

import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.request.VerifiableCredentialProof;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequest;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class VerifiableCredentialRequestVerifier
    implements VerifiableCredentialRequestTransformable {

  VerifiableCredentialRequest request;
  ServerConfiguration serverConfiguration;

  public VerifiableCredentialRequestVerifier(
      VerifiableCredentialRequest request, ServerConfiguration serverConfiguration) {
    this.request = request;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfNotContainsRequiredParams();
    throwExceptionIfUnSupportedFormat();
    throwExceptionIfInvalidProof();
  }

  void throwExceptionIfNotContainsRequiredParams() {
    if (!request.isFormatDefined()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "credential request must contains format");
    }
  }

  void throwExceptionIfUnSupportedFormat() {
    if (!serverConfiguration
        .credentialIssuerMetadata()
        .isSupportedFormat(request.format().value())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          String.format("unsupported credential format (%s)", request.format().value()));
    }
  }

  void throwExceptionIfInvalidProof() {
    if (!request.hasProof()) {
      return;
    }
    VerifiableCredentialProof verifiableCredentialProof = request.proof();
    if (!verifiableCredentialProof.isProofTypeDefined()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request contains proof, proof entity must define proof_type");
    }
    if (verifiableCredentialProof.isJwtType() && !verifiableCredentialProof.hasJwt()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request proof_type is jwt, proof entity must contains jwt claim");
    }
    if (verifiableCredentialProof.isCwtType() && !verifiableCredentialProof.hasJwt()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "When credential request proof_type is cwt, proof entity must contains cwt claim");
    }
  }
}
