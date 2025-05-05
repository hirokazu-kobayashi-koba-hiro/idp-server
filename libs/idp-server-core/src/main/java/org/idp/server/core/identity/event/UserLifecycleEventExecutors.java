package org.idp.server.core.identity.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class UserLifecycleEventExecutors implements Iterable<UserLifecycleEventExecutor> {

  List<UserLifecycleEventExecutor> executors;

  public UserLifecycleEventExecutors() {
    this.executors = new ArrayList<>();
  }

  public UserLifecycleEventExecutors(List<UserLifecycleEventExecutor> executors) {
    this.executors = executors;
  }

  @Override
  public Iterator<UserLifecycleEventExecutor> iterator() {
    return executors.iterator();
  }
}
