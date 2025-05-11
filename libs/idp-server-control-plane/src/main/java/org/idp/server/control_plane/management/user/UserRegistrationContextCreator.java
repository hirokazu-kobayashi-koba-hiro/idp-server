package org.idp.server.control_plane.management.user;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.user.io.UserRegistrationRequest;
import org.idp.server.core.identity.User;

public class UserRegistrationContextCreator {

    UserRegistrationRequest request;
    JsonConverter jsonConverter;

    public UserRegistrationContextCreator(UserRegistrationRequest request) {
        this.request = request;
        this.jsonConverter = JsonConverter.snakeCaseInstance();
    }

    public UserRegistrationContext create() {
        User user = jsonConverter.read(request.get("user"), User.class);
        boolean dryRun = request.isDryRun();
        return new UserRegistrationContext(user, dryRun);
    }
}
