package org.idp.server.core.authentication.fidouaf.deletion;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.log.LoggerWrapper;
import org.idp.server.core.authentication.fidouaf.*;
import org.idp.server.core.authentication.repository.AuthenticationConfigurationQueryRepository;
import org.idp.server.core.identity.User;
import org.idp.server.core.identity.event.UserLifecycleEvent;
import org.idp.server.core.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.identity.event.UserLifecycleEventResult;
import org.idp.server.core.identity.event.UserLifecycleType;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class ExternalFidoUafServerUserDataDeletionExecutor implements UserLifecycleEventExecutor {

  FidoUafExecutors fidoUafExecutors;
  AuthenticationConfigurationQueryRepository configurationQueryRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(ExternalFidoUafServerUserDataDeletionExecutor.class);

  public ExternalFidoUafServerUserDataDeletionExecutor(FidoUafExecutors fidoUafExecutors, AuthenticationConfigurationQueryRepository configurationQueryRepository) {
    this.fidoUafExecutors = fidoUafExecutors;
    this.configurationQueryRepository = configurationQueryRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.DELETE;
  }

  @Override
  public String name() {
    return "fido-uaf-deletion";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    if (userLifecycleEvent.lifecycleType() == UserLifecycleType.DELETE) {
      User user = userLifecycleEvent.user();
      return user.enabledFidoUaf();
    }
    return false;
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      FidoUafConfiguration fidoUafConfiguration = configurationQueryRepository.get(tenant, "fido-uaf", FidoUafConfiguration.class);

      FidoUafExecutor fidoUafExecutor = fidoUafExecutors.get(fidoUafConfiguration.type());

      Map<String, Object> request = new HashMap<>();
      // TODO dynamic mapping request
      FidoUafExecutionRequest fidoUafExecutionRequest = new FidoUafExecutionRequest(request);
      FidoUafExecutionResult executionResult = fidoUafExecutor.deleteKey(tenant, fidoUafExecutionRequest, fidoUafConfiguration);

      if (!executionResult.isSuccess()) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", executionResult.status());
        data.put("contents", executionResult.contents());
        return UserLifecycleEventResult.failure(name(), data);
      }

      return UserLifecycleEventResult.success(name(), executionResult.contents());
    } catch (Exception e) {
      Map<String, Object> data = new HashMap<>();
      data.put("message", e.getMessage());
      return UserLifecycleEventResult.failure(name(), data);
    }
  }
}
