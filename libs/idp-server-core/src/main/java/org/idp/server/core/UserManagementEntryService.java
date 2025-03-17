package org.idp.server.core;

import java.util.List;
import org.idp.server.core.basic.sql.Transactional;
import org.idp.server.core.api.UserManagementApi;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.user.UserRepository;

// TODO
@Transactional
public class UserManagementEntryService implements UserManagementApi {

  TenantRepository tenantRepository;
  UserRepository userRepository;

  public UserManagementEntryService(TenantRepository tenantRepository, UserRepository userRepository) {
    this.tenantRepository = tenantRepository;
    this.userRepository = userRepository;
  }

  @Override
  public void register(Tenant tenant, User user) {
    userRepository.register(tenant, user);
  }

  @Override
  public User get(TenantIdentifier tenantIdentifier, String userId) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userRepository.get(userId);
  }

  @Override
  public User findBy(Tenant tenant, String email, String providerId) {
    return userRepository.findBy(tenant, email, providerId);
  }

  @Override
  public void update(User user) {
    userRepository.update(user);
  }

  @Override
  public User findByProvider(String tokenIssuer, String providerId, String providerUserId) {
    return userRepository.findByProvider(tokenIssuer, providerId, providerUserId);
  }

  @Override
  public List<User> find(TenantIdentifier tenantIdentifier, int limit, int offset) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    return userRepository.findList(tenant, limit, offset);
  }


}
