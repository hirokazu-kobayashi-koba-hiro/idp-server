FROM nginx:latest

# Copy server certificates
COPY server.crt /etc/nginx/server.crt
COPY server.key /etc/nginx/server.key

# Copy SSL and mTLS configurations
COPY ssl.conf /etc/nginx/conf.d/ssl.conf
COPY mtls.conf /etc/nginx/mtls.conf

# Create directory for client certificates (mTLS)
RUN mkdir -p /etc/nginx/certs

# Copy test client certificate for simple verification
COPY test-client.pem /etc/nginx/certs/test-client.pem

# Copy CA certificate for production mTLS (optional)
# COPY ca.crt /etc/nginx/certs/ca.crt
