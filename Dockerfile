# ---------- 阶段 1：构建前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /fe
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN pnpm config set registry https://registry.npmmirror.com \
 && pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

# ---------- 阶段 2：构建后端 ----------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build
COPY pom.xml .
COPY src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN mvn -q package -DskipTests

# ---------- 阶段 3：运行 ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
EXPOSE 8080
ENV AI_API_KEY=""
ENTRYPOINT ["java", "-jar", "app.jar"]