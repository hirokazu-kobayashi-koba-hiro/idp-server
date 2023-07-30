package org.idp.server.user;

import org.idp.server.handler.userinfo.UserinfoDelegate;
import org.idp.server.oauth.identity.User;
import org.idp.server.type.oauth.Subject;

public class UserDefaultService implements UserinfoDelegate {


    @Override
    public User getUser(Subject subject) {
        return null;
    }
}
