package org.idp.server.core.hook.webhook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.hook.HookRequest;

public class WebhookRequestBodyCreator {

  HookRequest hookRequest;
  WebhookDynamicBodyKeys webhookDynamicBodyKeys;
  WebhookStaticBody webhookStaticBody;

  public WebhookRequestBodyCreator(
      HookRequest hookRequest,
      WebhookDynamicBodyKeys webhookDynamicBodyKeys,
      WebhookStaticBody webhookStaticBody) {
    this.hookRequest = hookRequest;
    this.webhookDynamicBodyKeys = webhookDynamicBodyKeys;
    this.webhookStaticBody = webhookStaticBody;
  }

  public Map<String, Object> create() {
    Map<String, Object> body = new HashMap<>();

    if (webhookStaticBody.exists()) {
      body.putAll(webhookStaticBody.toMap());
    }

    for (String key : webhookDynamicBodyKeys) {
      Object value = hookRequest.getValue(key);
      body.put(key, value);
    }

    return body;
  }
}
