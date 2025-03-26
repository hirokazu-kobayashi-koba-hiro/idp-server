package org.idp.server.core.hook.webhook;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.hook.HookRequest;

public class WebhookRequestBodyCreator {

  HookRequest hookRequest;
  WebhookParameters webhookParameters;

  public WebhookRequestBodyCreator(HookRequest hookRequest, WebhookParameters webhookParameters) {
    this.hookRequest = hookRequest;
    this.webhookParameters = webhookParameters;
  }

  public Map<String, Object> create() {
    Map<String, Object> body = new HashMap<>();

    for (String key : webhookParameters) {
      Object value = hookRequest.getValue(key);
      body.put(key, value);
    }

    return body;
  }
}
