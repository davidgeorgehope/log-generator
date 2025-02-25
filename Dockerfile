FROM openjdk:21-jre-slim

WORKDIR /app

# Copy the compiled JAR file
COPY target/log-generator-0.0.1-SNAPSHOT.jar /app/log-generator-0.0.1-SNAPSHOT.jar

# Create log directories
RUN mkdir -p /var/log/nginx_frontend /var/log/nginx_backend /var/log/mysql

# Set the timezone to UTC
ENV TZ=UTC
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# The command will be overridden by Kubernetes
CMD ["java", "-jar", "/app/log-generator-0.0.1-SNAPSHOT.jar"] 