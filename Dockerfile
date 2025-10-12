FROM eclipse-temurin:25-jdk-jammy AS build

COPY . .
RUN ./gradlew installDist --no-daemon

FROM eclipse-temurin:25-jdk-jammy AS runtime

WORKDIR /app

COPY --from=build /build/install/Modmail/bin/Modmail bin/Modmail
COPY --from=build /build/install/Modmail/lib/ lib/

ENTRYPOINT ["bin/Modmail"]
