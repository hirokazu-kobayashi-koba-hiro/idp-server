package org.idp.server.control_plane.management.user;

import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserRegistrationStatus;
import org.idp.server.core.identity.User;

import java.util.Map;

public class UserRegistrationContext {

    User user;
    boolean dryRun;

    public UserRegistrationContext(User user, boolean dryRun) {
        this.user = user;
        this.dryRun = dryRun;
    }

    public User user() {
        return user;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public UserManagementResponse toResponse() {
        Map<String, Object> contents = Map.of("user", user.toMap(), "dry_run", dryRun);
        return new UserManagementResponse(UserRegistrationStatus.OK, contents);
    }
}
