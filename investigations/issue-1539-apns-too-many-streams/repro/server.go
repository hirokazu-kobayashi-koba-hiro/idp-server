package main

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"strconv"
	"sync/atomic"
	"time"
)

func main() {
	delayMs := 3000
	if v := os.Getenv("DELAY_MS"); v != "" {
		if d, err := strconv.Atoi(v); err == nil {
			delayMs = d
		}
	}
	delay := time.Duration(delayMs) * time.Millisecond

	maxStreams := 1
	if v := os.Getenv("MAX_STREAMS"); v != "" {
		if m, err := strconv.Atoi(v); err == nil {
			maxStreams = m
		}
	}

	var inflight int64
	var peak int64
	h := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		n := atomic.AddInt64(&inflight, 1)
		for {
			p := atomic.LoadInt64(&peak)
			if n <= p || atomic.CompareAndSwapInt64(&peak, p, n) {
				break
			}
		}
		if delay > 0 {
			time.Sleep(delay)
		}
		atomic.AddInt64(&inflight, -1)
		fmt.Fprintf(w, "ok %s\n", r.URL.Path)
	})

	srv := &http.Server{
		Addr:    ":8443",
		Handler: h,
		HTTP2:   &http.HTTP2Config{MaxConcurrentStreams: maxStreams},
	}
	log.Printf("listening https://localhost:8443 (MaxConcurrentStreams=%d, delay=%v)", maxStreams, delay)
	log.Fatal(srv.ListenAndServeTLS("cert.pem", "key.pem"))
}
