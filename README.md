# Aplikace pro evidenci počítačů s možností analýzy neobvyklého chování systému ve spojitosti s kyberbezpečností - Backend
## Autor: Bc. Vladyslav Babyč

Backend aplikace je implementován v programovacím jazyce Java s použitím frameworku Spring Boot. 
Tato část aplikace poskytuje REST API pro webové rozhraní, komunikaci s agentem, správu detekčních pravidel, čtení auditních záznamů
i vzdálený deploy agenta.

## Lokalní spuštění

Požadavky:

- Java 21
- Maven 3.9+
- PostgreSQL nebo jiná kompatibilní databáze dle lokalni konfigurace

Spuštění backendu:

Před lokálním spuštěním backendu je potřeba naplnit soubor `application.properties` relevantními parametry.

V případě, že databáze aplikace již běží je možné použít následující příkaz.

```bash
cd be-evidence_and_cyber
mvn spring-boot:run
```

Pokud databáze aplikace není spuštěna je možné ji spustit s pomocí dockeru příkazem:
```bash
docker run --name db-container -p 5432:5432 
-e POSTGRES_USER=<postgres-user> 
-e POSTGRES_PASSWORD=<postgres-password>
-e POSTGRES_DB=evidence_and_cyber
-v evidence_and_cyber_volume:/var/lib/postgresql/data 
-d postgres
```

Build JAR balíčku:

```bash
cd be-evidence_and_cyber
mvn clean package
```

## Docker image

Dockerfile backendu používá jako build context kořen tohoto repositáře, proto je potřeba spouštět příkazy z kořenové složky projektu.

Vytvoření image a její nahrani do Docker Hubu:

```bash
docker buildx build \
  --platform linux/amd64 \
  -f be-evidence_and_cyber/Dockerfile \
  -t <docker-hub-username>/<backend-image-name>:<tag-name> \
  --push \
  .
```

V příkazu výše je použit `buildx`, tedy Docker plugin umožňující sestavit image kompatibilní s více architekturami systémů.

## Spuštění backendu na cílovem serveru

```bash
docker run --rm \
  --name <backend-container-name> \
  -p <host-backend-port>:8080 \
  -e SPRING_DATASOURCE_URL=<jdbc-url> \
  -e SPRING_DATASOURCE_USERNAME=<db-username> \
  -e SPRING_DATASOURCE_PASSWORD=<db-password> \
  -e APP_AGENT_ACCESS_SHARED_TOKEN=<agent-shared-token> \
  -e APP_AGENT_DEPLOYMENT_BACKEND_BASE_URL=<public-backend-base-url> \
  -e APP_LDAP_ENABLED=<true-or-false> \
  -e APP_LDAP_URL=<ldap-url> \
  -e APP_LDAP_DOMAIN=<ldap-domain> \
  -e APP_LDAP_ROOT_DN=<ldap-root-dn> \
  -e APP_LDAP_REQUIRED_GROUP=<required-ad-group> \
  -e APP_NOTIFICATION_ENABLED=<true-or-false> \
  -e APP_NOTIFICATION_CLIENT_ID=<graph-client-id> \
  -e APP_NOTIFICATION_CLIENT_SECRET=<graph-client-secret> \
  -e APP_NOTIFICATION_TENANT_ID=<graph-tenant-id> \
  -e APP_NOTIFICATION_SENDER=<notification-sender-mailbox> \
  -d <docker-hub-username>/<backend-image-name>:<tag-name>
```

## Důležité poznámky k nasazení

- Backendový image obsahuje deployment balíček agenta a pomocné skripty pro vzdálenou instalaci přes WinRM. Tento balíček je při buildu image převzat z repositáře agenta, a proto je nutné, aby byly backendová i agentní část dostupné v rámci stejného Docker build contextu.
- Pro funkční vzdálený deployment agenta musí být v rámci síťové infrastury povoleno spouštět WinRM příkazy a v backendu správně nastavena hodnota `APP_AGENT_DEPLOYMENT_BACKEND_BASE_URL`, aby si cílové zařízení mohlo stáhnout instalační ZIP balíček.
- Citlivé hodnoty, jako jsou databázové přístupy, LDAP konfigurace nebo token pro komunikaci s agentem, není z důvodu bezpečnosti možné 
ukládat přímo do repositáře a měli by být předány až při spuštění kontejneru nebo pomocí orchestrační vrstvy.
