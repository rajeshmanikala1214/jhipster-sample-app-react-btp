# Deploying to SAP BTP Cloud Foundry (Java 25 + SAP HANA Cloud)

This fork of [jhipster/jhipster-sample-app-react](https://github.com/jhipster/jhipster-sample-app-react)
is prepared to run as **one Cloud Foundry app** (Spring Boot REST API + React UI in one jar) on
**SapMachine 25**, persisting data in **SAP HANA Cloud** through Spring Data JPA / Hibernate.

## Target environment

| Item | Value |
|---|---|
| Account type | SAP BTP trial |
| Region | US East (VA) – AWS (`us10`) |
| Cloud Foundry API endpoint | `https://api.cf.us10-001.hana.ondemand.com` |
| Cloud Foundry org / space | `71026ba5trial` / `dev` |
| Org memory quota | 4 GB |
| SAP HANA Cloud instance | `java-web-deploy` (plan `hana-free`, created in space `dev`) |
| Database service used by the app | `hana` / plan `schema` → instance `jhipster-hana-schema` (created by the MTA) |

No passwords, keys, or service credentials belong in this file or anywhere in the repository.

## What was changed versus the upstream sample

| File | Change |
|---|---|
| `pom.xml` | `java.version` 21 → **25**; new Maven profile `cf` adding the SAP HANA JDBC driver (`ngdbc`) and the Liquibase HANA extension |
| `src/main/resources/config/application-cf.yml` | New Spring profile: HANA datasource from the service binding (`VCAP_SERVICES`), `HANADialect`, schema for Hibernate and Liquibase |
| `src/main/resources/config/application-prod.yml` | MySQL-only connection settings moved into the MySQL URL so they are not sent to the HANA driver |
| `src/main/resources/config/liquibase/master.xml` | Column-type properties for `dbms="hana"` |
| `mta.yaml` | Deployment descriptor: app module + HANA schema service |
| `secrets.mtaext.example` | Template for deploy-time secrets (the real `secrets.mtaext` is git-ignored) |

## Why `mta.yaml` (and not `manifest.yml`)

Both work. `mta.yaml` is used because, in one `cf deploy`, it:

- builds the jar (`mbt build`),
- **creates the HANA `schema` service instance and binds it** to the app,
- sets the buildpack, memory, health check and SapMachine 25 environment variables,
- is SAP's standard format for BTP and grows naturally if an approuter, XSUAA/IAS, or Destination service is added later.

With `manifest.yml` + `cf push`, the HANA service instance would have to be created and bound manually with separate `cf` commands.

## Prerequisites (local machine)

- JDK 25 (SapMachine 25 recommended) – `java -version` must show 25
- Maven 3.9+ (Node.js is downloaded automatically by the build)
- [Cloud Foundry CLI v8](https://github.com/cloudfoundry/cli) and the MultiApps plugin: `cf install-plugin multiapps`
- [Cloud MTA Build Tool](https://sap.github.io/cloud-mta-build-tool/): `npm install -g mbt`
- The HANA Cloud instance `java-web-deploy` must be **running** (free/trial instances stop every night – start it in SAP HANA Cloud Central)

## Deploy

```bash
# 1. Secrets (once)
cp secrets.mtaext.example secrets.mtaext   # then paste a generated base64 JWT secret

# 2. Build the MTA archive
mbt build

# 3. Log in and deploy
cf login -a https://api.cf.us10-001.hana.ondemand.com
cf target -o 71026ba5trial -s dev
cf deploy mta_archives/jhipster-sample-app-react_0.0.1.mtar -e secrets.mtaext
```

On first start Liquibase creates the tables in the bound HANA schema and loads the default users
(`admin` / `user`). **Change those default passwords immediately after the first login.**

## Verify

```bash
cf apps                                  # app should be "started"
cf logs jhipster-react-app --recent      # look for "SapMachine 25" and Liquibase success
```

Open the route shown by `cf apps` in a browser.
