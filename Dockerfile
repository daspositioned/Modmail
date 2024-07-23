FROM eclipse-temurin:21.0.4_7-jdk-jammy as build

COPY . .
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:21.0.4_7-jdk-jammy as runtime

WORKDIR /app

COPY --from=build /build/install/Modmail/bin/Modmail bin/Modmail
COPY --from=build /build/install/Modmail/lib/ lib/

ENTRYPOINT ["bin/Modmail"]
