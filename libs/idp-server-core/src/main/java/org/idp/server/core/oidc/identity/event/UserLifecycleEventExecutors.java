/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.identity.event;

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
