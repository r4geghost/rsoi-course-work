FROM maven:3.8-amazoncorretto-19 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM amazoncorretto:19 as builder
WORKDIR auth_service
ARG JAR_FILE=target/*.jar
COPY --from=build ${JAR_FILE} auth_service.jar
RUN java -Djarmode=layertools -jar auth_service.jar extract
LABEL authors="Михаил Дюсов ИУ7-11М"

FROM amazoncorretto:19
ENV PORT=8040
WORKDIR auth_service
COPY --from=builder auth_service/spring-boot-loader/ ./
COPY --from=builder auth_service/dependencies/ ./
COPY --from=builder auth_service/snapshot-dependencies/ ./
COPY --from=builder auth_service/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]