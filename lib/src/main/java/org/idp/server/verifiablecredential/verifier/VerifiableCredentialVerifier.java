package org.idp.server.verifiablecredential.verifier;

import java.time.LocalDateTime;
import org.idp.server.basic.date.SystemDateTime;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.mtls.ClientCertification;
import org.idp.server.oauth.mtls.ClientCertificationThumbprint;
import org.idp.server.oauth.mtls.ClientCertificationThumbprintCalculator;
import org.idp.server.oauth.rar.AuthorizationDetails;
import org.idp.server.oauth.token.AccessToken;
import org.idp.server.token.OAuthToken;
import org.idp.server.tokenintrospection.exception.TokenInvalidException;
import org.idp.server.type.mtls.ClientCert;
import org.idp.server.verifiablecredential.CredentialRequestParameters;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialTokenInvalidException;
import org.idp.server.verifiablecredential.request.VerifiableCredentialProof;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class VerifiableCredentialVerifier implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  CredentialRequestParameters parameters;
  ServerConfiguration serverConfiguration;

  public VerifiableCredentialVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      CredentialRequestParameters parameters,
      ServerConfiguration serverConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.serverConfiguration = serverConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    throwExceptionIfNotFoundToken();
    throwExceptionIfUnMatchClientCert();
    throwExceptionIfNotGranted();
    throwExceptionIfNotContainsRequiredParams();
    throwExceptionIfUnSupportedFormat();
    throwExceptionIfInvalidProof();
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!serverConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "unsupported verifiable credential");
    }
  }

  void throwExceptionIfNotFoundToken() {
    if (!oAuthToken.exists()) {
      throw new TokenInvalidException("not found token");
    }
    LocalDateTime now = SystemDateTime.now();
    if (oAuthToken.isExpire(now)) {
      throw new TokenInvalidException("token is expired");
    }
  }

  void throwExceptionIfUnMatchClientCert() {
    if (!oAuthToken.hasClientCertification()) {
      return;
    }
    if (!clientCert.exists()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token is sender constrained, but mtls client cert does not exists");
    }
    try {
      ClientCertification clientCertification = ClientCertification.parse(clientCert.plainValue());
      ClientCertificationThumbprintCalculator calculator =
          new ClientCertificationThumbprintCalculator(clientCertification);
      ClientCertificationThumbprint thumbprint = calculator.calculate();
      AccessToken accessToken = oAuthToken.accessToken();
      if (!accessToken.matchThumbprint(thumbprint)) {
        throw new VerifiableCredentialTokenInvalidException(
            "access token and mtls client cert is unmatch");
      }
    } catch (X509CertInvalidException e) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token is sender constrained, but mtls client cert is invalid format", e);
    }
  }

  void throwExceptionIfNotGranted() {
    AccessToken accessToken = oAuthToken.accessToken();
    if (!accessToken.hasAuthorizationDetails()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token have to contain authorization details at credential endpoint");
    }
    AuthorizationDetails authorizationDetails = accessToken.authorizationDetails();
    if (!authorizationDetails.hasVerifiableCredential()) {
      throw new VerifiableCredentialTokenInvalidException(
          "access token have to contain authorization details at credential endpoint");
    }
  }

  void throwExceptionIfNotContainsRequiredParams() {
    if (!parameters.isDefined()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "credential request must contains format");
    }
  }

  void throwExceptionIfUnSupportedFormat() {
    if (!serverConfiguration
        .credentialIssuerMetadata()
        .isSupportedFormat(parameters.format().value())) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          String.format("unsupported credential format (%s)", parameters.format().value()));
    }
  }

  void throwExceptionIfInvalidProof() {
    if (!parameters.hasProof()) {
      return;
    }
    try {
      VerifiableCredentialProof verifiableCredentialProof =
          transformProof(parameters.proofEntity());
      if (!verifiableCredentialProof.isDefined()) {
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
    } catch (VerifiableCredentialRequestInvalidException exception) {
      throw new VerifiableCredentialBadRequestException("invalid_request", exception.getMessage());
    }
  }
}
