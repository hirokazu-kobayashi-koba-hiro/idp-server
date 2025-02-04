package org.idp.server.handler.configuration;

import org.idp.server.configuration.ClientConfigurationNotFoundException;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementResponse;
import org.idp.server.handler.configuration.io.ClientConfigurationManagementStatus;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientConfigurationErrorHandler {

    Logger log = Logger.getLogger(ClientConfigurationErrorHandler.class.getName());

    public ClientConfigurationManagementResponse handle(Exception exception) {

        if (exception instanceof ClientConfigurationNotFoundException) {
            log.log(Level.WARNING, exception.getMessage(), exception);
            return new ClientConfigurationManagementResponse(ClientConfigurationManagementStatus.NOT_FOUND,
                    Map.of("error", "invalid_client", "error_description", exception.getMessage()));
        }
        log.log(Level.SEVERE, exception.getMessage(), exception);
        return new ClientConfigurationManagementResponse(ClientConfigurationManagementStatus.SERVER_ERROR,
                Map.of("error", "server_error", "error_description", exception.getMessage()));
    }
}
