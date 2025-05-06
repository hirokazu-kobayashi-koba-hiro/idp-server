package org.idp.server.core.oidc.configuration.handler;

import java.util.Map;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.oidc.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementResponse;
import org.idp.server.core.oidc.configuration.handler.io.ClientConfigurationManagementStatus;

public class ClientConfigurationErrorHandler {

  LoggerWrapper log = LoggerWrapper.getLogger(ClientConfigurationErrorHandler.class);

  public ClientConfigurationManagementResponse handle(Exception exception) {

    if (exception instanceof ClientConfigurationNotFoundException) {
      log.warn(exception.getMessage());
      return new ClientConfigurationManagementResponse(ClientConfigurationManagementStatus.NOT_FOUND, Map.of("error", "invalid_client", "error_description", exception.getMessage()));
    }
    log.error(exception.getMessage(), exception);
    return new ClientConfigurationManagementResponse(ClientConfigurationManagementStatus.SERVER_ERROR, Map.of("error", "server_error", "error_description", exception.getMessage()));
  }
}
