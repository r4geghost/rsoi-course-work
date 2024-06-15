FROM maven:3.8-amazoncorretto-19 AS build
COPY . .
RUN mvn clean package -DskipTests

FROM amazoncorretto:19 as builder
WORKDIR statistics_service
ARG JAR_FILE=target/*.jar
COPY --from=build ${JAR_FILE} statistics_service.jar
RUN java -Djarmode=layertools -jar statistics_service.jar extract
LABEL authors="Михаил Дюсов ИУ7-11М"

FROM amazoncorretto:19
ENV PORT=8030
WORKDIR statistics_service
COPY --from=builder statistics_service/spring-boot-loader/ ./
COPY --from=builder statistics_service/dependencies/ ./
COPY --from=builder statistics_service/snapshot-dependencies/ ./
COPY --from=builder statistics_service/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]