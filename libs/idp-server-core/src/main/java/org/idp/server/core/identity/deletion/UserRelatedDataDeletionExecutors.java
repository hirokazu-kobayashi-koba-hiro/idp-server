package org.idp.server.core.identity.deletion;

import java.util.Iterator;
import java.util.List;

public class UserRelatedDataDeletionExecutors implements Iterable<UserRelatedDataDeletionExecutor> {

  List<UserRelatedDataDeletionExecutor> executors;

  public UserRelatedDataDeletionExecutors(List<UserRelatedDataDeletionExecutor> executors) {
    this.executors = executors;
  }

  @Override
  public Iterator<UserRelatedDataDeletionExecutor> iterator() {
    return executors.iterator();
  }
}
