package org.idp.server.basic.sql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Logger;

public class TransactionInterceptor implements InvocationHandler {
  private final Object target;
  Logger log = Logger.getLogger(TransactionInterceptor.class.getName());

  public TransactionInterceptor(Object target) {
    this.target = target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    boolean isTransactional =
        method.isAnnotationPresent(Transactional.class)
            || target.getClass().isAnnotationPresent(Transactional.class);

    if (isTransactional) {
      log.info("begin transaction: " + target.getClass().getName() + ": " + method.getName());
      TransactionManager.beginTransaction();
      try {
        Object result = method.invoke(target, args);
        TransactionManager.commitTransaction();
        log.info("commit transaction: " + target.getClass().getName() + ": " + method.getName());
        return result;
      } catch (Exception e) {
        TransactionManager.rollbackTransaction();
        log.info("rollback transaction: " + target.getClass().getName() + ": " + method.getName());
        throw e;
      }
    } else {
      return method.invoke(target, args);
    }
  }

  @SuppressWarnings("unchecked")
  public static <T> T createProxy(T target, Class<T> interfaceType) {
    return (T)
        Proxy.newProxyInstance(
            interfaceType.getClassLoader(),
            new Class<?>[] {interfaceType},
            new TransactionInterceptor(target));
  }
}
