FROM eclipse-temurin:17-jre
WORKDIR /app

RUN useradd -m appuser

# Crear carpeta uploads y dar permisos (todavía como root)
RUN mkdir -p /app/uploads && chown -R appuser:appuser /app/uploads

COPY target/*.jar app.jar

# Ejecutar como usuario no-root
USER appuser

EXPOSE 3000
ENV JAVA_OPTS=""

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
