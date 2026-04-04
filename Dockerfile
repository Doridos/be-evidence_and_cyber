FROM maven:3.9.9-eclipse-temurin-21 AS agent-build
WORKDIR /workspace/agent-evidence_and_cyber
COPY agent-evidence_and_cyber/pom.xml ./
COPY agent-evidence_and_cyber/src ./src
RUN mvn -q -DskipTests package

FROM maven:3.9.9-eclipse-temurin-21 AS backend-build
WORKDIR /workspace/be-evidence_and_cyber
COPY be-evidence_and_cyber/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY be-evidence_and_cyber/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime

ENV DEBIAN_FRONTEND=noninteractive
ENV PYTHONUNBUFFERED=1
ENV JAVA_OPTS=""

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-pip ca-certificates curl \
    && pip3 install --no-cache-dir pywinrm==0.5.0 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY --from=backend-build /workspace/be-evidence_and_cyber/target/be-evidence-and-cyber-*.jar /app/app.jar
COPY --from=agent-build /workspace/agent-evidence_and_cyber/target/agent-evidence-and-cyber-*.jar /opt/evidence/agent-package/target/
COPY agent-evidence_and_cyber/windows /opt/evidence/agent-package/windows
COPY agent-evidence_and_cyber/windows-runtime /opt/evidence/agent-package/windows-runtime
COPY be-evidence_and_cyber/scripts /opt/evidence/deployment-scripts

ENV APP_AGENT_DEPLOYMENT_PYTHON_EXECUTABLE=python3 \
    APP_AGENT_DEPLOYMENT_HELPER_SCRIPT_PATH=/opt/evidence/deployment-scripts/agent_winrm_deploy.py \
    APP_AGENT_DEPLOYMENT_PACKAGE_SCRIPTS_DIR=/opt/evidence/agent-package/windows \
    APP_AGENT_DEPLOYMENT_PACKAGE_JAR_DIR=/opt/evidence/agent-package/target \
    APP_AGENT_DEPLOYMENT_PACKAGE_RUNTIME_DIR=/opt/evidence/agent-package/windows-runtime

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
