# Imagem da API para o Railway (#74). Contexto de build: a pasta backend/ (Root Directory do serviço).
# Estágio 1: compila com o JDK 17. Os testes não rodam aqui (precisam de MySQL): o CI já roda ./mvnw test
# em todo PR, e a main só aceita merge com o check verde.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

# Dependências primeiro: enquanto o pom.xml não muda, esta camada vem do cache
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests && cp target/backend-*.jar app.jar

# Estágio 2: só o JRE 17 e o jar, com um usuário sem root
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system rosie && useradd --system --gid rosie --no-create-home rosie
COPY --from=build /build/app.jar app.jar
USER rosie

# O Railway define PORT (o perfil prod lê server.port=${PORT:8080}); EXPOSE só documenta o padrão
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# Fuso de São Paulo, como no desenvolvimento: o Railway roda em UTC, e as datas gravadas com LocalDateTime.now()
# e @CreationTimestamp (pedido, troca, conta) sairiam 3 horas adiantadas.
# No Railway vale o startCommand do railway.json (o mesmo comando); este serve para o docker run local
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Duser.timezone=America/Sao_Paulo", "-jar", "/app/app.jar"]
