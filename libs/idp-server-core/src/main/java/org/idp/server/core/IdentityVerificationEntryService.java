package org.idp.server.core;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.basic.datasource.Transaction;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.json.JsonNodeWrapper;
import org.idp.server.core.basic.json.schema.JsonSchemaDefinition;
import org.idp.server.core.basic.json.schema.JsonSchemaValidationResult;
import org.idp.server.core.basic.json.schema.JsonSchemaValidator;
import org.idp.server.core.identity.trustframework.*;
import org.idp.server.core.identity.trustframework.delegation.ExternalWorkflowDelegationClient;
import org.idp.server.core.identity.trustframework.delegation.WorkflowApplyingResult;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.type.security.RequestAttributes;

@Transaction
public class IdentityVerificationEntryService implements IdentityVerificationApi {

  IdentityVerificationConfigurationQueryRepository configurationQueryRepository;
  IdentityVerificationApplicationCommandRepository applicationCommandRepository;
  IdentityVerificationApplicationQueryRepository applicationQueryRepository;
  ExternalWorkflowDelegationClient externalWorkflowDelegationClient;
  TenantRepository tenantRepository;
  JsonConverter jsonConverter;

  public IdentityVerificationEntryService(
      IdentityVerificationConfigurationQueryRepository configurationQueryRepository,
      IdentityVerificationApplicationCommandRepository applicationCommandRepository,
      IdentityVerificationApplicationQueryRepository applicationQueryRepository,
      TenantRepository tenantRepository) {
    this.configurationQueryRepository = configurationQueryRepository;
    this.applicationCommandRepository = applicationCommandRepository;
    this.applicationQueryRepository = applicationQueryRepository;
    this.tenantRepository = tenantRepository;
    this.externalWorkflowDelegationClient = new ExternalWorkflowDelegationClient();
    // TODO remove
    this.jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
  }

  @Override
  public IdentityVerificationApplicationResponse apply(
      TenantIdentifier tenantIdentifier,
      IdentityVerificationType identityVerificationType,
      VerificationProcess verificationProcess,
      IdentityVerificationApplicationRequest request,
      RequestAttributes requestAttributes) {
    Tenant tenant = tenantRepository.get(tenantIdentifier);

    IdentityVerificationConfiguration verificationConfiguration =
        configurationQueryRepository.get(tenant, identityVerificationType);
    IdentityVerificationProcessConfiguration processConfiguration =
        verificationConfiguration.getProcessConfig(verificationProcess);
    JsonNodeWrapper definition =
        jsonConverter.readTree(processConfiguration.requestValidationSchema());
    JsonSchemaDefinition jsonSchemaDefinition = new JsonSchemaDefinition(definition);
    JsonSchemaValidator jsonSchemaValidator = new JsonSchemaValidator(jsonSchemaDefinition);

    JsonNodeWrapper requestJson = jsonConverter.readTree(request.toMap());
    JsonSchemaValidationResult validationResult = jsonSchemaValidator.validate(requestJson);

    if (!validationResult.isValid()) {

      Map<String, Object> response = new HashMap<>();
      response.put("error", "invalid_request");
      response.put("error_description", "identity verification application is invalid.");
      response.put("error_details", validationResult.errors());
      return IdentityVerificationApplicationResponse.CLIENT_ERROR(response);
    }

    WorkflowApplyingResult applyingResult =
        externalWorkflowDelegationClient.apply(request, processConfiguration);

    IdentityVerificationApplication application =
        IdentityVerificationApplication.create(request, applyingResult);
    applicationCommandRepository.register(tenant, application);

    Map<String, Object> response = new HashMap<>();
    response.put("application", application);
    return IdentityVerificationApplicationResponse.OK(response);
  }

  @Override
  public void callbackProcess() {}

  @Override
  public void callbackResult() {}
}
