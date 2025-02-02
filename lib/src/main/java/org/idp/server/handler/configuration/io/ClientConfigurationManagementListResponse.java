package org.idp.server.handler.configuration.io;

import java.util.Map;

public class ClientConfigurationManagementListResponse {

    ClientConfigurationManagementListStatus status;

    Map<String, Object> content;

    public ClientConfigurationManagementListResponse(ClientConfigurationManagementListStatus status, Map<String, Object> content) {
        this.status = status;
        this.content = content;
    }


    public Map<String, Object> content() {
        return content;
    }

    public int statusCode() {
        return status.statusCode();
    }
}
