# ---------- 阶段 1：构建前端 ----------
FROM node:20-alpine AS frontend
WORKDIR /fe
ENV COREPACK_NPM_REGISTRY=https://registry.npmmirror.com
RUN corepack enable
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml ./
RUN --mount=type=cache,target=/fe/.pnpm-store \
    pnpm config set registry https://registry.npmmirror.com \
 && pnpm install --frozen-lockfile
COPY frontend/ ./
RUN pnpm build

# ---------- 阶段 2：构建后端 ----------
FROM maven:3.9-eclipse-temurin-25 AS backend
WORKDIR /build

# 配置阿里云 Maven 镜像，加速依赖下载
RUN mkdir -p /root/.m2 && printf '%s\n' \
    '<settings>' \
    '  <mirrors>' \
    '    <mirror>' \
    '      <id>aliyun</id>' \
    '      <mirrorOf>central</mirrorOf>' \
    '      <url>https://maven.aliyun.com/repository/public</url>' \
    '    </mirror>' \
    '  </mirrors>' \
    '</settings>' > /root/.m2/settings.xml

COPY pom.xml .
# 先把依赖下载下来，单独成层；改代码不会触发这一层重跑
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline -B

COPY src ./src
COPY --from=frontend /fe/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests -B

# ---------- 阶段 3：运行 ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=backend /build/target/*.jar app.jar
EXPOSE 8080
ENV AI_API_KEY=""
ENTRYPOINT ["java", "-jar", "app.jar"]