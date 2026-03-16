# ─── ビルドステージ ────────────────────────────────
FROM clojure:lein AS builder
WORKDIR /app
COPY . .
RUN lein cljsbuild once vanilla-release
RUN lein cljsbuild once reagent-release
RUN lein uberjar

# ─── 実行ステージ ──────────────────────────────────
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/uberjar/todo-app-1.0.0-standalone.jar app.jar
CMD ["java", "-jar", "app.jar", "3"]
