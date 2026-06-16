import javax.net.ssl.*;
import java.net.URI;
import java.net.http.*;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/**
 * Reproduces JDK HttpClient HTTP/2 behavior: a single shared HttpClient,
 * blocking send() from many threads in a sustained loop (connection reuse churn),
 * against a server advertising a low MAX_CONCURRENT_STREAMS.
 *
 * Usage: java Repro.java [threads] [itersPerThread]
 */
public class Repro {
  public static void main(String[] args) throws Exception {
    int threads = args.length > 0 ? Integer.parseInt(args[0]) : 64;
    int iters = args.length > 1 ? Integer.parseInt(args[1]) : 50;
    int maxAttempts = args.length > 2 ? Integer.parseInt(args[2]) : 1; // 1 = no retry, 2 = one retry
    int numClients = args.length > 3 ? Integer.parseInt(args[3]) : 1; // number of HttpClient instances

    System.setProperty("jdk.internal.httpclient.disableHostnameVerification", "true");
    SSLContext ssl = SSLContext.getInstance("TLS");
    ssl.init(null, new TrustManager[] {
      new X509TrustManager() {
        public void checkClientTrusted(X509Certificate[] c, String a) {}
        public void checkServerTrusted(X509Certificate[] c, String a) {}
        public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
      }
    }, new java.security.SecureRandom());

    HttpClient[] clients = new HttpClient[numClients];
    for (int k = 0; k < numClients; k++) {
      clients[k] = HttpClient.newBuilder()
          .version(HttpClient.Version.HTTP_2)
          .sslContext(ssl)
          .build();
    }
    AtomicInteger rr = new AtomicInteger();

    AtomicInteger ok = new AtomicInteger();
    AtomicInteger err = new AtomicInteger();
    AtomicInteger tooMany = new AtomicInteger();
    Map<String, AtomicInteger> errKinds = new ConcurrentHashMap<>();

    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    int total = threads * iters;
    System.out.printf("threads=%d iters=%d total=%d clients=%d maxAttempts=%d (blocking send, HTTP_2)%n",
        threads, iters, total, numClients, maxAttempts);

    for (int t = 0; t < threads; t++) {
      pool.submit(() -> {
        try {
          start.await();
          for (int i = 0; i < iters; i++) {
            Exception last = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
              try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://localhost:8443/r"))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();
                HttpClient client = clients[rr.getAndIncrement() % numClients];
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) ok.incrementAndGet();
                else err.incrementAndGet();
                last = null;
                break;
              } catch (Exception e) {
                last = e;
                if (attempt < maxAttempts) {
                  try { Thread.sleep(100); } catch (InterruptedException ignore) {}
                }
              }
            }
            if (last != null) {
              err.incrementAndGet();
              Throwable c = last;
              while (c.getCause() != null && c.getCause() != c) c = c.getCause();
              String msg = c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
              errKinds.computeIfAbsent(msg, k -> new AtomicInteger()).incrementAndGet();
              if (msg != null && msg.contains("too many concurrent streams")) tooMany.incrementAndGet();
            }
          }
        } catch (InterruptedException ignored) {
        } finally {
          done.countDown();
        }
      });
    }

    long t0 = System.currentTimeMillis();
    start.countDown();
    done.await();
    long dt = System.currentTimeMillis() - t0;

    System.out.printf("%nDONE in %dms  ok=%d err=%d  (too_many_concurrent_streams=%d)%n",
        dt, ok.get(), err.get(), tooMany.get());
    if (!errKinds.isEmpty()) {
      System.out.println("error breakdown:");
      errKinds.forEach((k, v) -> System.out.printf("  %5d  %s%n", v.get(), k));
    }
    pool.shutdownNow();
    System.exit(0);
  }
}
