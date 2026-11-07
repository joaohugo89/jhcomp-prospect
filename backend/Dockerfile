# Backend JH.COMP Prospect — build multi-estagio.
#
# O estagio de build usa JDK + Maven; a imagem final leva apenas o JRE, o que
# mantem o compilador e o cache do Maven fora do que vai para producao.

# ---------- Estagio 1: build ----------
FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /build

# Copia so o pom primeiro: enquanto as dependencias nao mudarem, esta camada e
# reaproveitada e o download nao se repete a cada alteracao de codigo.
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src

# Os testes nao rodam aqui: exigem Docker (Testcontainers), que nao esta
# disponivel dentro do build. Eles rodam no CI e localmente via `mvn verify`.
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

# ---------- Estagio 2: runtime ----------
FROM eclipse-temurin:24-jre-alpine AS runtime

# Usuario sem privilegios: um processo comprometido nao deve ser root.
RUN addgroup -S jhcomp && adduser -S -G jhcomp jhcomp

WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

RUN chown -R jhcomp:jhcomp /app
USER jhcomp

EXPOSE 8081

# RNF-009: a JVM opera em UTC.
ENV TZ=UTC \
    JAVA_TOOL_OPTIONS="-Duser.timezone=UTC -XX:MaxRAMPercentage=75"

# O health check usa o proprio endpoint da aplicacao, nao apenas a porta: a
# porta abre antes de o Flyway e o pool de conexoes ficarem prontos.
HEALTHCHECK --interval=15s --timeout=5s --start-period=45s --retries=5 \
    CMD wget --quiet --tries=1 --spider http://127.0.0.1:8081/api/v1/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
