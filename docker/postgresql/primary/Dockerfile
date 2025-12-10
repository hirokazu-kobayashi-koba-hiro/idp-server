FROM postgres:15

# Install pg_cron and pg_partman extensions
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        postgresql-15-cron \
        postgresql-15-partman && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*
