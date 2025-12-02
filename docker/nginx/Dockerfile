FROM nginx:latest

# Copy server certificates
COPY server.crt /etc/nginx/server.crt
COPY server.key /etc/nginx/server.key

# Copy SSL and mTLS configurations
COPY ssl.conf /etc/nginx/conf.d/ssl.conf
COPY mtls.conf /etc/nginx/mtls.conf

# Create directory for client certificates (mTLS)
RUN mkdir -p /etc/nginx/certs

# Copy CA bundle for mTLS (includes self-signed + CA certificates)
COPY client-ca-bundle.pem /etc/nginx/certs/client-ca-bundle.pem
