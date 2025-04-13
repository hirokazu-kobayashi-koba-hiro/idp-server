package org.idp.server.core;

import java.util.List;
import org.idp.server.core.admin.UserManagementApi;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;

// TODO
@Transactional
public class UserManagementEntryService implements UserManagementApi {

  TenantRepository tenantRepository;
  UserRepository userRepository;

  public UserManagementEntryService(
      TenantRepository tenantRepository, UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  @Override
  public void register(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    userRepository.register(tenant, user);
  }

  @Override
  public User get(TenantIdentifier tenantIdentifier, String userId) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userRepository.get(tenant, userId);
  }

  @Override
  public User findBy(TenantIdentifier tenantIdentifier, String email, String providerId) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    return userRepository.findBy(tenant, email, providerId);
  }

  @Override
  public void update(TenantIdentifier tenantIdentifier, User user) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);
    userRepository.update(tenant, user);
  }

  @Override
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userRepository.findList(tenant, limit, offset);
  }
}
