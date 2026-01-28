/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.extension.verifiable_credentials.handler;

import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialErrorResponse;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialTokenInvalidException;
import org.idp.server.core.extension.verifiable_credentials.handler.io.BatchCredentialResponse;
import org.idp.server.core.extension.verifiable_credentials.handler.io.CredentialRequestStatus;
import org.idp.server.core.extension.verifiable_credentials.handler.io.CredentialResponse;
import org.idp.server.core.extension.verifiable_credentials.handler.io.DeferredCredentialResponse;
import org.idp.server.core.openid.oauth.configuration.exception.ClientConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.configuration.exception.ServerConfigurationNotFoundException;
import org.idp.server.core.openid.oauth.type.oauth.Error;
import org.idp.server.core.openid.oauth.type.oauth.ErrorDescription;
import org.idp.server.platform.log.LoggerWrapper;

public class CredentialRequestErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(CredentialRequestErrorHandler.class);

  public CredentialResponse handle(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(
          "Credential request failed: status=bad_request, error=invalid_token, description={}",
          badRequest.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(
          "Credential request failed: status=bad_request, error=invalid_request, description={}",
          badRequest.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(
          "Credential request failed: status=bad_request, error=invalid_client, description={}",
          exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(
          "Credential request failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      return new CredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(
        "Credential request failed: status=server_error, error={}",
        exception.getMessage(),
        exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new CredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public BatchCredentialResponse handleBatchRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(
          "Batch credential request failed: status=bad_request, error=invalid_token, description={}",
          badRequest.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(
          "Batch credential request failed: status=bad_request, error=invalid_request, description={}",
          badRequest.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(
          "Batch credential request failed: status=bad_request, error=invalid_client, description={}",
          exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(
          "Batch credential request failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      return new BatchCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(
        "Batch credential request failed: status=server_error, error={}",
        exception.getMessage(),
        exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new BatchCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }

  public DeferredCredentialResponse handleDeferredRequest(Exception exception) {
    if (exception instanceof VerifiableCredentialTokenInvalidException badRequest) {
      log.warn(
          "Deferred credential request failed: status=bad_request, error=invalid_token, description={}",
          badRequest.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_token"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof VerifiableCredentialBadRequestException badRequest) {
      log.warn(
          "Deferred credential request failed: status=bad_request, error=invalid_request, description={}",
          badRequest.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(badRequest.getMessage())));
    }
    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(
          "Deferred credential request failed: status=bad_request, error=invalid_client, description={}",
          exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_client"), new ErrorDescription(exception.getMessage())));
    }
    if (exception instanceof ServerConfigurationNotFoundException) {
      log.warn(
          "Deferred credential request failed: status=bad_request, error=invalid_request, description={}",
          exception.getMessage());
      return new DeferredCredentialResponse(
          CredentialRequestStatus.BAD_REQUEST,
          new VerifiableCredentialErrorResponse(
              new Error("invalid_request"), new ErrorDescription(exception.getMessage())));
    }
    log.error(
        "Deferred credential request failed: status=server_error, error={}",
        exception.getMessage(),
        exception);
    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    VerifiableCredentialErrorResponse errorResponse =
        new VerifiableCredentialErrorResponse(error, errorDescription);
    return new DeferredCredentialResponse(CredentialRequestStatus.SERVER_ERROR, errorResponse);
  }
}
