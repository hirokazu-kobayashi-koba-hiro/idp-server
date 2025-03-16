package org.idp.server.core;

import java.util.List;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.function.UserManagementFunction;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.user.UserService;

// TODO
@Transactional
public class UserManagementService implements UserManagementFunction {

  TenantService tenantService;
  UserService userService;

  public UserManagementService(TenantService tenantService, UserService userService) {
    this.tenantService = tenantService;
    this.userService = userService;
  }

  @Override
  public void register(Tenant tenant, User user) {
    userService.register(tenant, user);
  }

  @Override
  public User get(TenantIdentifier tenantIdentifier, String userId) {
    Tenant tenant = tenantService.get(tenantIdentifier);

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
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantService.get(tenantIdentifier);

    return userService.find(tenant, limit, offset);
  }

  @Override
  public boolean authenticate(User user, String rawPassword) {
    return userService.authenticate(user, rawPassword);
  }
}
