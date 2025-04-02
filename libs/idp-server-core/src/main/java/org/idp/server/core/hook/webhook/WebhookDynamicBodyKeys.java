package org.idp.server.core.hook.webhook;

import java.util.Iterator;
import java.util.List;

public class WebhookDynamicBodyKeys implements Iterable<String> {

  List<String> values;

  public WebhookDynamicBodyKeys() {}

  public WebhookDynamicBodyKeys(List<String> values) {
    this.values = values;
  }

  @Override
  public Iterator<String> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
