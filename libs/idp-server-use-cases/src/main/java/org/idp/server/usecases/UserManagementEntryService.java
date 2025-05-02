package org.idp.server.usecases;

import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.core.admin.UserManagementApi;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantRepository;

// TODO
@Transaction
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
    return userRepository.findByEmail(tenant, email, providerId);
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
