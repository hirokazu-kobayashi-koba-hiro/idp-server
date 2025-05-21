package org.idp.server.authentication.interactors.sms.external;

import java.util.Map;
import org.idp.server.basic.http.HttpRequestResult;

public class ExternalSmsAuthenticationHttpRequestResult {

  HttpRequestResult executionResult;

  public ExternalSmsAuthenticationHttpRequestResult(HttpRequestResult executionResult) {
    this.executionResult = executionResult;
  }

  public int statusCode() {
    return executionResult.statusCode();
  }

  public boolean isSuccess() {
    return executionResult.isSuccess();
  }

  public boolean isClientError() {
    return executionResult.isClientError();
  }

  public boolean isServerError() {
    return executionResult.isServerError();
  }

  public Map<String, Object> responseBody() {
    return executionResult.body().toMap();
  }
}
