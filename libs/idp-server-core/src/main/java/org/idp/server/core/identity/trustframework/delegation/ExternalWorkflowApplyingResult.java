package org.idp.server.core.identity.trustframework.delegation;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.http.HttpRequestResult;
import org.idp.server.core.basic.json.JsonNodeWrapper;

public class ExternalWorkflowApplyingResult {
  int statusCode;
  Map<String, List<String>> headers;
  JsonNodeWrapper body;

  public ExternalWorkflowApplyingResult() {}

  public ExternalWorkflowApplyingResult(HttpRequestResult httpRequestResult) {
    this.statusCode = httpRequestResult.statusCode();
    this.headers = httpRequestResult.headers();
    this.body = httpRequestResult.body();
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

  // TODO use response definition
  public ExternalWorkflowApplicationIdentifier extractApplicationIdentifierFromBody() {
    return new ExternalWorkflowApplicationIdentifier(extractValueFromBody("application_id"));
  }
}
