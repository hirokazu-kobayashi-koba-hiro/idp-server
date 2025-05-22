/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials.handler;

import org.idp.server.basic.type.oauth.Error;
import org.idp.server.basic.type.oauth.ErrorDescription;
import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialErrorResponse;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialTokenInvalidException;
import org.idp.server.core.extension.verifiable_credentials.handler.io.BatchCredentialResponse;
import org.idp.server.core.extension.verifiable_credentials.handler.io.CredentialRequestStatus;
import org.idp.server.core.extension.verifiable_credentials.handler.io.CredentialResponse;
import org.idp.server.core.extension.verifiable_credentials.handler.io.DeferredCredentialResponse;
import org.idp.server.core.oidc.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.platform.log.LoggerWrapper;

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
