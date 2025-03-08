package org.idp.server.core.handler.federation;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ServerConfigurationNotFoundException;
import org.idp.server.core.federation.FederatableIdProviderConfigurationNotFoundException;
import org.idp.server.core.handler.federation.io.FederationCallbackResponse;
import org.idp.server.core.handler.federation.io.FederationCallbackStatus;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.handler.federation.io.FederationRequestStatus;
import org.idp.server.core.type.oauth.Error;
import org.idp.server.core.type.oauth.ErrorDescription;

public class FederationErrorHandler {

  Logger log = Logger.getLogger(FederationErrorHandler.class.getName());

  public FederationRequestResponse handleRequest(Exception exception) {

    if (exception
        instanceof FederatableIdProviderConfigurationNotFoundException notFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new FederationRequestResponse(FederationRequestStatus.BAD_REQUEST);
    }

    if (exception instanceof FederationSessionNotFoundException notFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new FederationRequestResponse(FederationRequestStatus.BAD_REQUEST);
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new FederationRequestResponse(FederationRequestStatus.BAD_REQUEST);
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new FederationRequestResponse(FederationRequestStatus.BAD_REQUEST);
    }

    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new FederationRequestResponse(FederationRequestStatus.SERVER_ERROR);
  }

  public FederationCallbackResponse handleCallback(Exception exception) {
    if (exception
        instanceof FederatableIdProviderConfigurationNotFoundException notFoundException) {
      log.log(Level.WARNING, exception.getMessage(), exception);
      return new FederationCallbackResponse(FederationCallbackStatus.BAD_REQUEST);
    }

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new FederationCallbackResponse(FederationCallbackStatus.BAD_REQUEST);
    }

    if (exception instanceof ServerConfigurationNotFoundException) {
      log.log(Level.WARNING, "not found configuration");
      log.log(Level.WARNING, exception.getMessage(), exception);
      Error error = new Error("invalid_request");
      ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
      return new FederationCallbackResponse(FederationCallbackStatus.BAD_REQUEST);
    }

    Error error = new Error("server_error");
    ErrorDescription errorDescription = new ErrorDescription(exception.getMessage());
    log.log(Level.SEVERE, exception.getMessage(), exception);
    return new FederationCallbackResponse(FederationCallbackStatus.SERVER_ERROR);
  }
}
