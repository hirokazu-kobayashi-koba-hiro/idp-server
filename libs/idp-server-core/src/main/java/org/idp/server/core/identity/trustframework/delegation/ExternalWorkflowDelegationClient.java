package org.idp.server.core.identity.trustframework.delegation;

import org.idp.server.core.basic.http.HttpClientFactory;
import org.idp.server.core.basic.http.HttpRequestBaseParams;
import org.idp.server.core.basic.http.HttpRequestExecutor;
import org.idp.server.core.basic.http.HttpRequestResult;
import org.idp.server.core.identity.trustframework.application.IdentityVerificationApplicationRequest;
import org.idp.server.core.identity.trustframework.configuration.IdentityVerificationProcessConfiguration;

public class ExternalWorkflowDelegationClient {

  HttpRequestExecutor httpRequestExecutor;

  public ExternalWorkflowDelegationClient() {
    this.httpRequestExecutor = new HttpRequestExecutor(HttpClientFactory.defaultClient());
  }

  public WorkflowApplyingResult execute(
      IdentityVerificationApplicationRequest request,
      IdentityVerificationProcessConfiguration processConfig) {

    HttpRequestResult executionResult =
        httpRequestExecutor.execute(
            processConfig.httpRequestUrl(),
            processConfig.httpMethod(),
            processConfig.httpRequestHeaders(),
            new HttpRequestBaseParams(request.toMap()),
            processConfig.httpRequestDynamicBodyKeys(),
            processConfig.httpRequestStaticBody());

    return new WorkflowApplyingResult(executionResult);
  }
}
