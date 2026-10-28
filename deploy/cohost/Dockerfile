FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 cardadvisor \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin cardadvisor
WORKDIR /opt/card-advisor
COPY --chown=10001:10001 app.jar ./app.jar
USER 10001:10001
EXPOSE 18443
ENTRYPOINT ["java", "-jar", "/opt/card-advisor/app.jar"]
