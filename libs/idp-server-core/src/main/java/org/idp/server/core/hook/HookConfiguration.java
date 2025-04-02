package org.idp.server.core.hook;

import java.util.List;
import java.util.Map;
import org.idp.server.core.basic.json.JsonReadable;
import org.idp.server.core.hook.webhook.*;

public class HookConfiguration implements JsonReadable {

  String id;
  String trigger;
  String type;

  // webhook
  String webhookUrl;
  String webhookMethod;
  Map<String, String> webhookHeaders;
  List<String> webhookDynamicBodyKeys;
  Map<String, String> webhookStaticBody;

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

  public WebhookUrl webhookUrl() {
    return new WebhookUrl(webhookUrl);
  }

  public WebhookMethod webhookMethod() {
    return WebhookMethod.valueOf(webhookMethod);
  }

  public WebhookHeaders webhookHeaders() {
    return new WebhookHeaders(webhookHeaders);
  }

  public WebhookDynamicBodyKeys webhookDynamicBodyKeys() {
    return new WebhookDynamicBodyKeys(webhookDynamicBodyKeys);
  }

  public WebhookStaticBody webhookStaticBody() {
    return new WebhookStaticBody(webhookStaticBody);
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
