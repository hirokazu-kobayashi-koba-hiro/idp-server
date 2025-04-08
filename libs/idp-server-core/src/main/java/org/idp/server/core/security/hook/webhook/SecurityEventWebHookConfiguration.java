package org.idp.server.core.security.hook.webhook;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.security.hook.SecurityEventHookTriggerType;
import org.idp.server.core.security.hook.SecurityEventHookType;

public class SecurityEventWebHookConfiguration implements JsonReadable {

  String trigger;
  String type;

  String url;
  String method;
  Map<String, String> headers;
  List<String> dynamicBodyKeys;
  Map<String, Object> staticBody;

  public SecurityEventWebHookConfiguration() {}

  public SecurityEventHookTriggerType triggerType() {
    return SecurityEventHookTriggerType.valueOf(trigger);
  }

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public HttpRequestUrl webhookUrl() {
    return new HttpRequestUrl(url);
  }

  public HttpMethod webhookMethod() {
    return HttpMethod.valueOf(method);
  }

  public HttpRequestHeaders webhookHeaders() {
    return new HttpRequestHeaders(headers);
  }

  public HttpRequestDynamicBodyKeys webhookDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(dynamicBodyKeys);
  }

  public HttpRequestStaticBody webhookStaticBody() {
    return new HttpRequestStaticBody(staticBody);
  }
}
