# Backend Evidence & Cyber

Backend aplikace je implementovan v Java 21 a Spring Bootu. Tato cast aplikace poskytuje REST API pro webove rozhrani, komunikaci s agentem, spravu detekcnich pravidel, auditnich zaznamu i vzdaleny deployment agenta.

## Lokalne spusteni

Pozadavky:

- Java 21
- Maven 3.9+
- PostgreSQL nebo jina kompatibilni databaze dle lokalni konfigurace

Spusteni z adresare backendu:

```bash
cd be-evidence_and_cyber
mvn spring-boot:run
```

Build JAR balicku:

```bash
cd be-evidence_and_cyber
mvn clean package
```

## Docker image

Dockerfile backendu pouziva jako build context koren tohoto repozitare, proto je potreba prikazy spoustet z rootu projektu.

Vytvoreni multi-arch image a jeji nahrani do Docker Hubu:

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -f be-evidence_and_cyber/Dockerfile \
  -t <docker-hub-username>/<backend-image-name>:<tag-name> \
  --push \
  .
```

V prikazu vyse je pouzit `buildx`, tedy Docker plugin umoznujici sestavit image kompatibilni s vice architekturami.

## Spusteni backendu na cilovem serveru

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

## Dulezite poznamky k nasazeni

- Backendovy image obsahuje i deployment balicek agenta a pomocne skripty pro vzdalenou instalaci pres WinRM.
- Pro funkcni vzdaleny deployment agenta musi byt v backendu spravne nastavena hodnota `APP_AGENT_DEPLOYMENT_BACKEND_BASE_URL`, aby si cilove zarizeni mohlo stahnout instalacni ZIP balicek.
- Citlive hodnoty, jako jsou databazove pristupy, LDAP konfigurace nebo token pro komunikaci s agentem, nema smysl ukladat primo do repozitare a maji byt predany az pri spusteni kontejneru nebo pomoci orchestracni vrstvy.
