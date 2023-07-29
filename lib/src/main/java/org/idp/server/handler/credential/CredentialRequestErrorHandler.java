package org.idp.server.handler.credential;

import static org.idp.server.handler.token.io.TokenRequestStatus.*;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.configuration.ServerConfigurationNotFoundException;
import org.idp.server.handler.credential.io.BatchCredentialResponse;
import org.idp.server.handler.credential.io.CredentialRequestStatus;
import org.idp.server.handler.credential.io.CredentialResponse;
import org.idp.server.handler.credential.io.DeferredCredentialResponse;
import org.idp.server.type.oauth.Error;
import org.idp.server.type.oauth.ErrorDescription;
import org.idp.server.verifiablecredential.VerifiableCredentialErrorResponse;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialTokenInvalidException;

public class CredentialRequestErrorHandler {

  Logger log = Logger.getLogger(CredentialRequestErrorHandler.class.getName());

  public CredentialResponse handle(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.log(Level.SEVERE, exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new CredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public BatchCredentialResponse handleBatchRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.log(Level.SEVERE, exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new BatchCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public DeferredCredentialResponse handleDeferredRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.log(Level.SEVERE, exception.getMessage(), exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new DeferredCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }
}
