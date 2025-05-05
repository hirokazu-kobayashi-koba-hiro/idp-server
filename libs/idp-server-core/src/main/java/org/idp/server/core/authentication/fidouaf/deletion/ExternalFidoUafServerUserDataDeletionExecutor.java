package org.idp.server.core.authentication.fidouaf.deletion;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.fidouaf.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.UserLifecycleEvent;
import org.idp.server.core.identity.UserLifecycleOperation;
import org.idp.server.core.identity.deletion.UserDeletionResult;
import org.idp.server.core.identity.deletion.UserRelatedDataDeletionExecutor;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class ExternalFidoUafServerUserDataDeletionExecutor
    implements UserRelatedDataDeletionExecutor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;

  public ExternalFidoUafServerUserDataDeletionExecutor(
      FidoUafExecutors fidoUafExecutors,
      AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.fidoUafExecutors = fidoUafExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public String name() {
    return "fido-uaf-deletion";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    if (userLifecycleEvent.lifecycleOperation() == UserLifecycleOperation.DELETE) {
      User user = userLifecycleEvent.user();
      return user.enabledFidoUaf();
    }
    return false;
  }

  @Override
  public UserDeletionResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      FidoUafConfiguration fidoUafConfiguration =
          configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);

      FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

      Map<String, Object> request = new HashMap<>();
      // TODO dynamic mapping request
      FidoUafExecutionRequest fidoUafExecutionRequest = new FidoUafExecutionRequest(request);
      FidoUafExecutionResult executionResult =
          fidoUafExecutor.deleteKey(tenant, fidoUafExecutionRequest, fidoUafConfiguration);

      if (!executionResult.isSuccess()) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", executionResult.status());
        data.put("contents", executionResult.contents());
        return UserDeletionResult.failure(data);
      }

      return UserDeletionResult.success(executionResult.contents());
    } catch (Exception e) {
      Map<String, Object> data = new HashMap<>();
      data.put("message", e.getMessage());
      return UserDeletionResult.failure(data);
    }
  }
}
