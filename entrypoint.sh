# File: entrypoint.sh
#!/bin/sh
set -e

# ensure config dir exists for Spring Boot to pick up
mkdir -p /config

# substitute only the expected variables (prevents other envs from being expanded)
if [ -f /app/config/application.yml.tpl ]; then
  envsubst '$SPRING_KAFKA_BOOTSTRAP_SERVERS $SERVER_PORT $SPRING_KAFKA_LISTENER_CONCURRENCY' \
    < /app/config/application.yml.tpl > /config/application.yml
fi

exec java -jar /app/app.jar
