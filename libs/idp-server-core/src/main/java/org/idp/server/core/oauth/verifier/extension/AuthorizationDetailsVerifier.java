package org.idp.server.core.oauth.verifier.extension;

import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;
import org.idp.server.core.oauth.rar.AuthorizationDetails;
import org.idp.server.core.oauth.rar.AuthorizationDetailsInvalidException;

public class AuthorizationDetailsVerifier {
  AuthorizationDetails authorizationDetails;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public AuthorizationDetailsVerifier(
      AuthorizationDetails authorizationDetails,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.authorizationDetails = authorizationDetails;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public void verify() {
    throwExceptionIfNotContainsType();
    throwExceptionIfUnauthorizedType();
    throwExceptionIfUnSupportedType();
    throwExceptionIfUnauthorizedType();
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
          if (!serverConfiguration.isSupportedAuthorizationDetailsType(
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
}
