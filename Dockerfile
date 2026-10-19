FROM eclipse-temurin:22-jre

WORKDIR /app
COPY build/libs/eia-payment-domain-0.1.0-SNAPSHOT.jar app.jar

EXPOSE 8098
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
