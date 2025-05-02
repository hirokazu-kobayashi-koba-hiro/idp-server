package org.idp.server.core.verifiable_credential.handler;

import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.oidc.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.verifiable_credential.VerifiableCredentialErrorResponse;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialTokenInvalidException;
import org.idp.server.core.verifiable_credential.handler.io.BatchCredentialResponse;
import org.idp.server.core.verifiable_credential.handler.io.CredentialRequestStatus;
import org.idp.server.core.verifiable_credential.handler.io.CredentialResponse;
import org.idp.server.core.verifiable_credential.handler.io.DeferredCredentialResponse;

public class CredentialRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(CredentialRequestErrorHandler.class);

  public CredentialResponse handle(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new CredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public BatchCredentialResponse handleBatchRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(exception.getMessage());
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new BatchCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public DeferredCredentialResponse handleDeferredRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new DeferredCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }
}
