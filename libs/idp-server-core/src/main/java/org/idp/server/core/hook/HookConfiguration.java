package org.idp.server.core.hook;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.http.*;
import org.idp.server.core.basic.json.JsonReadable;

public class HookConfiguration implements JsonReadable {

  String id;
  String trigger;
  String type;

  // webhook
  String webhookUrl;
  String webhookMethod;
  Map<String, String> webhookHeaders;
  List<String> webhookDynamicBodyKeys;
  Map<String, Object> webhookStaticBody;

  // slack notification
  String slackUrl;
  String slackMessageTemplate;

  public HookConfiguration() {}

  public String id() {
    return id;
  }

  public HookTriggerType triggerType() {
    return HookTriggerType.valueOf(trigger);
  }

  public HookType hookType() {
    return new HookType(type);
  }

  public HttpRequestUrl webhookUrl() {
    return new HttpRequestUrl(webhookUrl);
  }

  public HttpMethod webhookMethod() {
    return HttpMethod.valueOf(webhookMethod);
  }

  public HttpRequestHeaders webhookHeaders() {
    return new HttpRequestHeaders(webhookHeaders);
  }

  public HttpRequestDynamicBodyKeys webhookDynamicBodyKeys() {
    return new HttpRequestDynamicBodyKeys(webhookDynamicBodyKeys);
  }

  public HttpRequestStaticBody webhookStaticBody() {
    return new HttpRequestStaticBody(webhookStaticBody);
  }

  public String slackUrl() {
    return slackUrl;
  }

  public String slackMessageTemplate() {
    return slackMessageTemplate;
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }
}
