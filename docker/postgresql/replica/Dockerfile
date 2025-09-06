FROM postgres:15

# Install additional tools
USER root

# Copy initialization script
COPY init-replica.sh /usr/local/bin/init-replica.sh
RUN chmod +x /usr/local/bin/init-replica.sh

# Custom entrypoint that handles replica initialization
COPY entrypoint-replica.sh /usr/local/bin/entrypoint-replica.sh
RUN chmod +x /usr/local/bin/entrypoint-replica.sh

USER postgres

ENTRYPOINT ["/usr/local/bin/entrypoint-replica.sh"]
CMD ["postgres"]