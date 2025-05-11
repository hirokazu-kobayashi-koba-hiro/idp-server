package org.idp.server.control_plane.management.user.io;

import java.util.Map;

public class UserManagementResponse {
    UserRegistrationStatus status;
    Map<String, Object> contents;

    public UserManagementResponse(UserRegistrationStatus status, Map<String, Object> contents) {
        this.status = status;
        this.contents = contents;
    }

    public UserRegistrationStatus status() {
        return status;
    }

    public int statusCode() {
        return status.statusCode();
    }

    public Map<String, Object> contents() {
        return contents;
    }
}
