# 后端统一 Dockerfile：用 build-arg MODULE 指定构建哪个服务
# docker-compose 里每个服务传不同的 MODULE（gateway/user-service/course-service/enroll-service/seat-service）
# 两阶段构建：第一阶段 maven 打包，第二阶段只留 JRE + app.jar，镜像更小
ARG MODULE=gateway

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
ARG MODULE
# 先只拷 pom，利用 Docker 层缓存加速依赖下载
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY user-service/pom.xml user-service/pom.xml
COPY course-service/pom.xml course-service/pom.xml
COPY enroll-service/pom.xml enroll-service/pom.xml
COPY seat-service/pom.xml seat-service/pom.xml
RUN mvn -q -B dependency:go-offline -pl ${MODULE} -am || true
# 再拷源码并打包（-am 把 common 一起打）
COPY . .
RUN mvn -q -B -DskipTests -pl ${MODULE} -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
ARG MODULE
ENV MODULE=${MODULE}
# 父 pom 里统一了 <finalName>app</finalName>，所以 jar 名固定为 app.jar
COPY --from=build /app/${MODULE}/target/app.jar /app/app.jar
EXPOSE 8080 8081 8082 8083 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
