package org.idp.server.usecases.control_plane.tenant_manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.datasource.Transaction;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.basic.type.security.RequestAttributes;
import org.idp.server.control_plane.base.definition.AdminPermissions;
import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.management.identity.user.UserManagementApi;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContext;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContextCreator;
import org.idp.server.control_plane.management.identity.user.io.UserManagementResponse;
import org.idp.server.control_plane.management.identity.user.io.UserManagementStatus;
import org.idp.server.control_plane.management.identity.user.io.UserRegistrationRequest;
import org.idp.server.control_plane.management.identity.user.io.UserUpdateRequest;
import org.idp.server.control_plane.management.identity.user.validator.UserRegistrationRequestValidationResult;
import org.idp.server.control_plane.management.identity.user.validator.UserRegistrationRequestValidator;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerificationResult;
import org.idp.server.control_plane.management.identity.user.verifier.UserRegistrationVerifier;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserIdentifier;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventPublisher;
import org.idp.server.core.identity.event.UserLifecycleType;
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
  UserLifecycleEventPublisher userLifecycleEventPublisher;
  LoggerWrapper log = LoggerWrapper.getLogger(UserManagementEntryService.class);

  public UserManagementEntryService(
      TenantQueryRepository tenantQueryRepository,
      UserQueryRepository userQueryRepository,
      UserCommandRepository userCommandRepository,
      PasswordEncodeDelegation passwordEncodeDelegation,
      UserLifecycleEventPublisher userLifecycleEventPublisher) {
    this.tenantQueryRepository = tenantQueryRepository;
    this.userQueryRepository = userQueryRepository;
    this.userCommandRepository = userCommandRepository;
    this.passwordEncodeDelegation = passwordEncodeDelegation;
    UserVerifier userVerifier = new UserVerifier(userQueryRepository);
    this.verifier = new UserRegistrationVerifier(userVerifier);
    this.userLifecycleEventPublisher = userLifecycleEventPublisher;
  }

  @Override
  public UserManagementResponse create(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserRegistrationRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("create");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    UserRegistrationRequestValidator validator =
        new UserRegistrationRequestValidator(request, dryRun);
    UserRegistrationRequestValidationResult validate = validator.validate();
    if (!validate.isValid()) {
      return validate.errorResponse();
    }

    UserRegistrationContextCreator userRegistrationContextCreator =
        new UserRegistrationContextCreator(tenant, request, dryRun, passwordEncodeDelegation);
    UserRegistrationContext context = userRegistrationContextCreator.create();

    UserRegistrationVerificationResult verificationResult = verifier.verify(context);
    if (!verificationResult.isValid()) {
      return verificationResult.errorResponse();
    }

    if (dryRun) {
      return context.toResponse();
    }

    userCommandRepository.register(tenant, context.user());

    return context.toResponse();
  }

  @Transaction(readOnly = true)
  @Override
  public UserManagementResponse findList(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      int limit,
      int offset,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("findList");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    List<User> users = userQueryRepository.findList(tenant, limit, offset);
    Map<String, Object> response = Map.of("list", users.stream().map(User::toMap).toList());

    return new UserManagementResponse(UserManagementStatus.OK, response);
  }

  @Transaction(readOnly = true)
  @Override
  public UserManagementResponse get(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes) {

    AdminPermissions permissions = getRequiredPermissions("get");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    User user = userQueryRepository.findById(tenant, userIdentifier);

    if (!user.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    return new UserManagementResponse(UserManagementStatus.OK, user.toMap());
  }

  @Override
  public UserManagementResponse update(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      UserUpdateRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("update");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);
    User before = userQueryRepository.findById(tenant, userIdentifier);

    if (!before.exists()) {
      return new UserManagementResponse(UserManagementStatus.NOT_FOUND, Map.of());
    }

    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, request.toMap());
    }
    userCommandRepository.update(tenant, operator);

    return new UserManagementResponse(UserManagementStatus.OK, request.toMap());
  }

  @Override
  public UserManagementResponse delete(
      TenantIdentifier tenantIdentifier,
      User operator,
      OAuthToken oAuthToken,
      UserIdentifier userIdentifier,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    AdminPermissions permissions = getRequiredPermissions("delete");
    if (!permissions.includesAll(operator.permissionsAsSet())) {
      Map<String, Object> response = new HashMap<>();
      response.put("error", "access_denied");
      response.put(
          "error_description",
          String.format(
              "permission denied required permission %s, but %s",
              permissions.valuesAsString(), operator.permissionsAsString()));
      log.warn(response.toString());
      return new UserManagementResponse(UserManagementStatus.FORBIDDEN, response);
    }

    Tenant tenant = tenantQueryRepository.get(tenantIdentifier);

    if (dryRun) {
      return new UserManagementResponse(UserManagementStatus.OK, Map.of());
    }

    User user = userQueryRepository.get(tenant, userIdentifier);
    userCommandRepository.delete(tenant, userIdentifier);

    UserLifecycleEvent userLifecycleEvent =
        new UserLifecycleEvent(tenant, user, UserLifecycleType.DELETE);
    userLifecycleEventPublisher.publish(userLifecycleEvent);

    return new UserManagementResponse(UserManagementStatus.OK, Map.of());
  }
}
