FROM harbor.dms-shared.in/dockerhub/library/eclipse-temurin:18-jre-alpine

COPY build/libs/lib /libs
COPY build/libs/dms-document-service-*.jar /application.jar

ENTRYPOINT java \
           -Xverify:none -XX:TieredStopAtLevel=1 \
           -Dspring.jmx.enabled=false \
           $JAVA_OPTS \
           -cp "/application.jar:/libs/*" \
           br.com.dms.Application

EXPOSE 8080