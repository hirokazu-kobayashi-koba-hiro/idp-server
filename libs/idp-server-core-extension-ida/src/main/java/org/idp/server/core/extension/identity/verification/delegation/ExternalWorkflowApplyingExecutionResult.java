package org.idp.server.core.extension.identity.verification.delegation;

import java.util.List;
import java.util.Map;
import org.idp.server.basic.http.HttpRequestResult;
import org.idp.server.basic.json.JsonNodeWrapper;

public class ExternalWorkflowApplyingExecutionResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  public ExternalWorkflowApplyingExecutionResult() {}

  public ExternalWorkflowApplyingExecutionResult(HttpRequestResult httpRequestResult) {
    this.statusCode = httpRequestResult.statusCode();
    this.headers = httpRequestResult.headers();
    this.body = httpRequestResult.body();
  }

  public boolean isClientError() {
    return statusCode >= 400 && statusCode < 500;
  }

  public boolean isServerError() {
    return statusCode >= 500;
  }

  public int statusCode() {
    return statusCode;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public JsonNodeWrapper body() {
    return body;
  }

  public String extractValueFromBody(String key) {
    return body.getValueOrEmptyAsString(key);
  }
}
