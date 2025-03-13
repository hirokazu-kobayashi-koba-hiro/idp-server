package org.idp.server.core.adapters;

import org.idp.server.core.UserManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.user.UserService;

import java.util.List;

//TODO
@Transactional
public class UserManagementApiImpl implements UserManagementApi {


    UserService userService;

    public UserManagementApiImpl(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void register(Tenant tenant, User user) {
        userService.register(tenant, user);
    }

    @Override
    public User get(String userId) {
        return userService.get(userId);
    }

    @Override
    public User findBy(Tenant tenant, String email, String providerId) {
        return userService.findBy(tenant, email, providerId);
    }

    @Override
    public void update(User user) {
        userService.update(user);
    }

    @Override
    public User findByProvider(String tokenIssuer, String providerId, String providerUserId) {
        return userService.findByProvider(tokenIssuer, providerId, providerUserId);
    }

    @Override
    public List<User> find(Tenant tenant, int limit, int offset) {
        return userService.find(tenant, limit, offset);
    }

    @Override
    public boolean authenticate(User user, String rawPassword) {
        return userService.authenticate(user, rawPassword);
    }
}
