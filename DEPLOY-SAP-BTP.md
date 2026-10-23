# Deploying to SAP BTP Cloud Foundry (Java 25 + PostgreSQL + Redis)

This fork of [jhipster/jhipster-sample-app-react](https://github.com/jhipster/jhipster-sample-app-react)
runs as **one Cloud Foundry app** (Spring Boot REST API + React UI in one jar) on **SapMachine 25**.
Data is stored in **PostgreSQL, hyperscaler option** through Spring Data JPA / Hibernate (tables managed by Liquibase),
and cached in **Redis, hyperscaler option** through Redisson (Spring Cache + Hibernate second-level cache).

## Target environment

| Item                       | Value                                                                         |
| -------------------------- | ----------------------------------------------------------------------------- |
| Account type               | SAP BTP trial                                                                 |
| Region                     | US East (VA) – AWS (`us10`)                                                   |
| Cloud Foundry API endpoint | `https://api.cf.us10-001.hana.ondemand.com`                                   |
| Cloud Foundry org / space  | `c09726f8trial` / `dev`                                                       |
| Org memory quota           | 4 GB (the app uses 1.5 GB)                                                    |
| PostgreSQL instance        | `ro-bank-postgres-sql` – service `postgresql-db`, plan `trial`, PostgreSQL 16 |
| Redis instance             | `ro-bank-redis` – service `redis-cache`, plan `trial`, Redis 6.0              |

Both service instances already exist in space `dev`; the MTA only **binds** them (`org.cloudfoundry.existing-service`),
so undeploying or redeploying the app never deletes their data.
No passwords, keys, or service credentials belong in this file or anywhere in the repository.

## What was changed versus the upstream sample

| File                            | Change                                                                                                                                                                                                            |
| ------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `.yo-rc.json` + generated files | Regenerated with JHipster 9.4.0 using `prodDatabaseType: postgresql`, `cacheProvider: redis`, `hibernateCache: redis` (PostgreSQL driver, Redisson, Liquibase column types, Testcontainers, docker-compose files) |
| `pom.xml`                       | `java.version` **25**                                                                                                                                                                                             |
| `CacheConfiguration.java`       | Redisson client exposed as a bean; password can come from `application.redis.password`; new cache `labelsList`                                                                                                    |
| `ApplicationProperties.java`    | New property `application.redis.password`                                                                                                                                                                         |
| `LabelResource.java`            | `GET /api/labels` cached in Redis (`@Cacheable`), evicted by POST/PUT/PATCH/DELETE (`@CacheEvict`)                                                                                                                |
| `RedisDemoResource.java`        | Admin-only `/api/admin/redis/**`: list keys with type/TTL, store and read a value with a TTL                                                                                                                      |
| `application-cf.yml`            | Datasource and Redis settings read from the service bindings (`VCAP_SERVICES`)                                                                                                                                    |
| `mta.yaml`                      | App module bound to `ro-bank-postgres-sql` and `ro-bank-redis`                                                                                                                                                    |

## Build and deploy (pipeline or manual)

Build: `mbt build` (runs `mvn -B -ntp -Pprod clean package -DskipTests`; needs **JDK 25** on the build agent; Node is downloaded by Maven).

Deploy the produced `mta_archives/jhipster-sample-app-react_0.0.2.mtar` with the JWT secret extension:

```bash
cf login -a https://api.cf.us10-001.hana.ondemand.com
cf target -o c09726f8trial -s dev
cf deploy mta_archives/jhipster-sample-app-react_0.0.2.mtar -e secrets.mtaext
```

`secrets.mtaext` (git-ignored) is created from `secrets.mtaext.example`. In a pipeline, provide it as a secret file or
set `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` on the app instead.

On first start Liquibase creates the tables and loads the default users (`admin` / `user`).
**Change those default passwords immediately after the first login.**

## Verify

```bash
cf app jhipster-react-app                  # requested state started, 1/1 instances running
cf logs jhipster-react-app --recent        # SapMachine 25, Liquibase success, Redisson connected
curl https://<route>/management/health     # "db" and "redis" components UP
```

Get a token and exercise the API:

```bash
TOKEN=$(curl -s -X POST https://<route>/api/authenticate -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"<admin password>"}' | sed 's/.*"id_token":"\([^"]*\)".*/\1/')
curl -s -X POST https://<route>/api/labels -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' -d '{"label":"groceries"}'
curl -s https://<route>/api/labels -H "Authorization: Bearer $TOKEN"
```

Redis cache hit / miss: call `GET /api/labels` twice. The first call logs
`REST request to get all Labels: cache miss` plus a `select ... from label` SQL line; the second logs neither.
Any write on labels evicts the cache, and entries expire after 300 seconds (`jhipster.cache.redis.expiration`).

Redis keys and TTL (admin token):

```bash
curl -s "https://<route>/api/admin/redis/keys?pattern=*" -H "Authorization: Bearer $TOKEN"
curl -s -X PUT "https://<route>/api/admin/redis/demo/greeting?ttlSeconds=60" -H "Authorization: Bearer $TOKEN" -H 'Content-Type: text/plain' -d 'hello'
curl -s https://<route>/api/admin/redis/demo/greeting -H "Authorization: Bearer $TOKEN"   # ttlMillis counts down, 404 after expiry
```

Inspect the databases directly (both endpoints are private, so tunnel through the app):

```bash
cf enable-ssh jhipster-react-app && cf restart jhipster-react-app
cf create-service-key ro-bank-postgres-sql pg-key && cf service-key ro-bank-postgres-sql pg-key
cf ssh -L 15432:<pg hostname>:<pg port> jhipster-react-app -N      # then DBeaver/psql on localhost:15432, sslmode=require
cf create-service-key ro-bank-redis redis-key && cf service-key ro-bank-redis redis-key
cf ssh -L 6666:<redis hostname>:<redis port> jhipster-react-app -N # then: redis-cli --tls -p 6666 -a <password>
```

## Troubleshooting

| Symptom                                                   | Fix                                                                                           |
| --------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `Could not resolve placeholder 'vcap.services.ro-bank-…'` | App not bound to that instance name – check `cf services`                                     |
| Redis `MOVED` errors                                      | Instance is clustered: `cluster_mode` is read from the binding; confirm with `cf service-key` |
| Redis TLS handshake error                                 | Binding has no TLS: set env `REDIS_SCHEME=redis`                                              |
| `UnsupportedClassVersionError`                            | App not running on SapMachine 25 – check the `JBP_CONFIG_*` properties in `mta.yaml`          |
| Crash with exit status 137                                | Out of memory – keep `memory: 1536M` or higher                                                |
