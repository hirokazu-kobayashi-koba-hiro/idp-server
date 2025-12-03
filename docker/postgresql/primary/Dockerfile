FROM postgres:15

# Install pg_cron extension
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        postgresql-15-cron && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
