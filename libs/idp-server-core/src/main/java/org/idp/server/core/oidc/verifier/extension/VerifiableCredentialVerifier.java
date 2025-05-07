package org.idp.server.core.oidc.verifier.extension;

import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.rar.AuthorizationDetails;
import org.idp.server.core.oidc.rar.AuthorizationDetailsInvalidException;
import org.idp.server.core.oidc.vc.VerifiableCredentialInvalidException;

public class VerifiableCredentialVerifier {
  AuthorizationDetails authorizationDetails;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;

  public VerifiableCredentialVerifier(
      AuthorizationDetails authorizationDetails,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationDetails = authorizationDetails;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    throwExceptionIfNotContainsType();
    throwExceptionIfUnauthorizedType();
    throwExceptionIfUnSupportedType();
    throwExceptionIfUnauthorizedType();
    throwIfUnSupportedVerifiableCredential();
  }

  void throwExceptionIfNotContainsType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationDetail.hasType()) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details", "authorization details does not contains type");
          }
        });
  }

  void throwExceptionIfUnSupportedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!authorizationServerConfiguration.isSupportedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unsupported authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }

  void throwExceptionIfUnauthorizedType() {
    authorizationDetails.forEach(
        authorizationDetail -> {
          if (!clientConfiguration.isAuthorizedAuthorizationDetailsType(
              authorizationDetail.type())) {
            throw new AuthorizationDetailsInvalidException(
                "invalid_authorization_details",
                String.format(
                    "unauthorized authorization details type (%s)", authorizationDetail.type()));
          }
        });
  }

  void throwIfUnSupportedVerifiableCredential() {
    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialInvalidException(
          "invalid_request", "unsupported verifiable credential");
    }
  }
}
