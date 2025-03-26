package org.idp.server.core.hook;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HookConfigurations implements Iterable<HookConfiguration> {

  List<HookConfiguration> values;

  public HookConfigurations() {
    this.values = new ArrayList<>();
  }

  public HookConfigurations(List<HookConfiguration> values) {
    this.values = values;
  }

  @Override
  public Iterator<HookConfiguration> iterator() {
    return values.iterator();
  }

  public boolean exists() {
    return values != null && !values.isEmpty();
  }
}
