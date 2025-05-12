package org.idp.server.usecases.control_plane;

import java.util.List;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.user.UserManagementApi;
import org.idp.server.control_plane.management.user.UserRegistrationContext;
import org.idp.server.control_plane.management.user.UserRegistrationContextCreator;
import org.idp.server.control_plane.management.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.user.validator.UserRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.user.validator.UserRegistrationRequestValidator;
import org.idp.server.control_plane.management.user.verifier.UserRegistrationVerificationResult;
import org.idp.server.control_plane.management.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.repository.UserCommandRepository;
import org.idp.server.core.identity.repository.UserQueryRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.core.multi_tenancy.tenant.TenantQueryRepository;
import org.idp.server.core.token.OAuthToken;

@Transaction
public class UserManagementEntryService implements UserManagementApi {

  TenantQueryRepository tenantQueryRepository;
  UserQueryRepository userQueryRepository;
  UserCommandRepository userCommandRepository;
  PasswordEncodeDelegation passwordEncodeDelegation;
  UserRegistrationVerifier verifier;

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    this.verifier = new UserRegistrationVerifier(userVerifier);
  }

  @Override
  public UserManagementResponse register(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    UserRegistrationRequestValidator validator = new UserRegistrationRequestValidator(request);
    UserRegistrationRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserRegistrationContextCreator userRegistrationContextCreator =
        new UserRegistrationContextCreator(tenant, request, passwordEncodeDelegation);
    UserRegistrationContext context = userRegistrationContextCreator.create();

    UserRegistrationVerificationResult verificationResult = verifier.verify(context);
    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (context.isDryRun()) {
      return context.toResponse();
    }

    userCommandRepository.register(tenant, context.user());

    return context.toResponse();
  }

  @Override
  public User get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return userQueryRepository.get(tenant, userIdentifier);
  }

  @Override
  public void update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserUpdateRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    // TODO
    userCommandRepository.update(tenant, operator);
  }

  @Override
  public List<User> find(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    return userQueryRepository.findList(tenant, limit, offset);
  }
}
