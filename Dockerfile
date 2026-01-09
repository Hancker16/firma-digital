FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -m appuser
USER appuser

# Crear carpeta uploads y dar permisos al usuario
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app/uploads

COPY target/*.jar app.jar

EXPOSE 3000
ENV JAVA_OPTS=""

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
