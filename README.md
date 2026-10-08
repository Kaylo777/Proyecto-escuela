# Colegio — Spring Security + JWT sobre Spring Cloud

Trabajo de investigación e implementación de **Spring Security y JWT** integrados en una
arquitectura de **Spring Cloud**, con un frontend **Angular** que incluye login.

El sistema es una aplicación de gestión escolar: microservicios de `alumnos` y
`administracion` (docentes), protegidos por un **API Gateway** que autentica y autoriza
por roles, sobre **Eureka** (registro) y **Config Server** (configuración centralizada en
un repositorio Git remoto), con **RabbitMQ** para el intercambio de eventos entre
servicios y **Spring Boot Admin** para monitoreo.

---

## 1. Arquitectura

```
                        ┌──────────────┐
   Navegador  ────────► │    Angular   │  :4200  (login, alumnos, docentes, seguridad)
                        └──────┬───────┘
                               │  Authorization: Bearer <accessToken>
                               ▼
                        ┌──────────────┐
                        │ API Gateway  │  :8080  ← único punto de entrada público
                        │ valida token │         autoriza por rol y reenvía con
                        │ autoriza     │         X-Service-Token
                        └──────┬───────┘
                               │  lb://  (Spring Cloud LoadBalancer + Eureka)
                 ┌─────────────┴─────────────┬────────────────┐
                 ▼                           ▼                ▼
        ┌──────────────────┐      ┌──────────────────┐   ┌─────────────────┐
        │   auth-service   │      │    alumnos       │   │ administracion  │
        │    identidad     │      │   :8101 (H2)     │   │  :8102 (H2)     │
        │ login + emite    │      │  exige token     │   │  exige token    │
        │ y renueva tokens │      │  de servicio     │   │  de servicio    │
        └──────────────────┘      └────────┬─────────┘   └────────┬────────┘
                                           │ evento    evento     │
                                           └───────────┬───────────┘
                                                       ▼
                                               ┌──────────────┐
                                               │  RabbitMQ    │  :5672 (UI :15672)
                                               └──────────────┘

        Eureka :8761 · Config Server :8888 · Spring Boot Admin :9090
```

| Servicio | Puerto | Management | Rol |
|---|---|---|---|
| `gateway` | 8080 | — | Único punto público: valida el JWT, autoriza por rol, agrega `X-Service-Token`, enruta |
| `auth-service` | 8100 | 9110 | Identidad: login, emisión y renovación de tokens (SSO) |
| `alumnos` | 8101 | 9111 | CRUD de alumnos (H2), publica `alumno.creado` |
| `administracion` | 8102 | 9112 | CRUD de docentes (H2), publica `docente.creado` |
| `registry` | 8761 | — | Eureka: registro y descubrimiento de servicios |
| `config-server` | 8888 | — | Configuración centralizada (backend Git) |
| `admin-server` | 9090 | — | Spring Boot Admin: métricas y estado |
| RabbitMQ | 5672 / 15672 | — | Bus de eventos entre microservicios |
| Angular | 4200 | — | Frontend con login |

Los puertos de *management* son **separados de los de la API** (9110/9111/9112): las
consultas de salud (Eureka, Spring Boot Admin, probes de Docker) no compiten con el
tráfico de negocio, y la exposición de actuator se **acota a `health, info, metrics`**
para no filtrar `env`, `beans` ni `configprops`.

### Stack

| Capa | Tecnología | Versión |
|---|---|---|
| Backend | Spring Boot | 4.0.8 |
| Cloud | Spring Cloud | 2025.1.3 |
| Seguridad | Spring Security + JJWT | 6.x / 0.12.6 |
| Frontend | Angular | 22.1 |
| Base de datos | H2 (archivo) | — |
| Mensajería | RabbitMQ | 3.x (Docker) |
| Build | Maven | 3.9 (wrapper por servicio y agregador en la raíz) |
| Contenedores | Docker / Compose | multi-stage, 8 imágenes |

---

## 2. Requisitos

- **Java 17 o superior** (Spring Boot 4 no compila con Java 11)
- **Node.js 20+** y npm (solo para el frontend)
- **Docker Desktop** — para RabbitMQ o para levantar el stack completo con `docker compose`
- **Maven** — no hace falta instalarlo: se usa el `mvnw.cmd`
- **Git** — para el repositorio de configuración

```powershell
java -version     # debe decir 17 o superior
docker --version  # opcional
```

---

## 3. Cómo ejecutarlo

### 3.1 Todo el stack con Docker (recomendado)

Desde la raíz del proyecto:

```powershell
docker compose build      # construye las 8 imágenes (7 servicios + frontend)
docker compose up -d      # levanta todo con los healthchecks y el orden correcto
```

Accesos:

| Qué | Dónde | Credencial |
|---|---|---|
| Frontend (SPA con login) | http://localhost:4200 | `admin/admin123` o `user/user123` |
| API Gateway | http://localhost:8080 | Bearer JWT |
| Eureka | http://localhost:8761 | — |
| Config Server | http://localhost:8888 | — |
| Spring Boot Admin | http://localhost:9090 | `admin/admin123` |
| RabbitMQ UI | http://localhost:15672 | `guest/guest` |

Los microservicios de negocio también quedan publicados (8100/8101/8102 y 9110/9111/9112)
para poder demostrar que **entrar directo a ellos con Postman devuelve 403**.

Para bajar todo:

```powershell
docker compose down
```

> Dentro de Docker los servicios se corren solo en la red del compose (gateway, Eureka,
> Config Server, RabbitMQ se ven por su nombre de contenedor) gracias a las variables
> `EUREKA_HOST`, `CONFIG_SERVER_HOST` y `RABBITMQ_HOST` definidas en `docker-compose.yml`:
> las mismas configs sirven local y en contenedores (los valores por defecto son
> `localhost`).

### 3.2 Sin Docker, por partes

**Compilar el backend** (con el POM agregador, 7 módulos de una vez):

```powershell
.\mvnw.cmd -DskipTests package
```

O servicio por servicio con cada `mvnw.cmd` propio (es lo que usa `iniciar-backend.ps1`).

**Preparar el repositorio de configuración** (solo la primera vez, o cuando cambie la
plantilla `config-template/`):

```powershell
.\iniciar-config-repo.ps1
```

Crea `config-repo/` (repo Git independiente) a partir de `config-template/`. En este
repositorio vive TODA la configuración que sirve el Config Server (ver §7). El mismo
repo está publicado en GitHub:
`https://github.com/Kaylo777/colegio-config.git` (privado).

**Levantar RabbitMQ** (las operaciones de escritura lo necesitan):

```powershell
docker compose up -d rabbitmq
```

**Levantar el backend**:

```powershell
.\iniciar-backend.ps1
```

Arranca en orden `registry` → `config-server` → `auth-service` → `alumnos` →
`administracion` → `gateway` → `admin-server`, esperando a que cada puerto abra antes de
seguir. Los logs quedan en `backend\logs\`.

Para detener todo: `.\detener-backend.ps1`.

**Levantar el frontend**:

```powershell
cd frontend
npm install
npm start          # http://localhost:4200
```

### 3.3 Probar la API

Una batería de 41 verificaciones automatizadas contra el gateway (roles, tokens,
token de servicio, DTOs, config, RabbitMQ):

```powershell
.\probar-api.ps1
```

Con el stack sano termina en `Total: 41   Fallos: 0` (ver §9).

### 3.4 Trabajar en Eclipse / VS Code

El backend se importa completo gracias al POM agregador de la raíz (`File → New →
Maven Project → Existing Maven Projects` seleccionando el `pom.xml` raíz). En VS Code el
repo trae `.vscode/extensions.json` y `.vscode/settings.json` compartidos (Extension Pack
for Java + Angular Language Service).

---

## 4. Usuarios de prueba

Definidos en el repositorio de configuración (`auth-service.yml`), con contraseñas
hasheadas en **BCrypt** (`{bcrypt}...`), nunca en texto plano. El **auth-service** es el
único servicio que las conoce.

| Usuario | Contraseña | Rol | Puede |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Leer **y** escribir (POST, PUT, DELETE) |
| `user` | `user123` | `USER` | Solo leer (`GET`) |
| `monitor` | `monitor123` | `ADMIN` | Cuenta de monitoreo para Spring Boot Admin: solo `/actuator/**` |

---

## 5. Endpoints y matriz de roles

La matriz se aplica **en el gateway** (el único autorizador). `USER` solo lee;
`ADMIN` lee y escribe.

| Endpoint | Sin token | `USER` | `ADMIN` |
|---|---|---|---|
| `POST /auth/login` | 200 | — | — |
| `POST /auth/refresh` | 200 | — | — |
| `GET /auth/me` | 401 | 200 | 200 |
| `GET /api/alumnos` | 401 | 200 | 200 |
| `POST /api/alumnos` | 401 | **403** | 200 |
| `PUT /api/alumnos/{id}` | 401 | **403** | 200 |
| `DELETE /api/alumnos/{id}` | 401 | **403** | 200 |
| `GET /api/administracion/docentes` | 401 | 200 | 200 |
| `POST /api/administracion/docentes` | 401 | **403** | 200 |
| `GET /actuator/health` | 200 | 200 | 200 |
| `GET /actuator/env` | 401 | 401 | 200 (HTTP Basic `monitor`) |

---

## 6. Cómo funciona la seguridad

### 6.1 Separación: el gateway autoriza, el auth-service identifica

Dos microservicios distintos (corrección 2 del docente: la seguridad no vive en el
gateway):

- **`auth-service`**: única entidad que conoce las credenciales. `POST /auth/login`
  valida contra BCrypt, firma el **access token** (HS256, 15 min) y el **refresh token**
  (HS256, 8 h), y los renueva con `POST /auth/refresh`.
- **`gateway`**: valida la firma del token, aplica la matriz de roles y reenvía. No
  conoce contraseñas ni emite tokens.

Decodificando el token emitido se ve su contenido: `issuer=colegio-microservicios`,
`sub=admin`, `roles=[ADMIN]`, `type=access`.

### 6.2 Dos secretos distintos

`app.jwt.secret` firma los **access tokens** y `app.jwt.refresh-secret` los **refresh
tokens**. Al usar claves diferentes, un refresh token no puede usarse como access token
ni al revés. Verificado en *Pruebas realizadas*.

### 6.3 Los microservicios ya no validan el JWT: token de servicio (correcciones 1 y 4)

Los microservicios de negocio **no tienen Spring Security** ni validan el JWT (eso era
"Spring Security por microservicio como si fueran monolitos", corrección 1). La
confianza se resuelve con una cabecera interna:

```
gateway ──► X-Service-Token: <secreto> ──► alumnos / administracion
```

- El gateway **agrega** `X-Service-Token` a cada petición que reenvía.
- `alumnos` y `administracion` corren un **filtro de token de servicio** (Spring Security
  `OncePerRequestFilter` más liviano, sin JWT): si la cabecera no coincide, devuelven
  **403**.
- Entrar directo a `http://localhost:8101` o `:8102` con Postman devuelve 403: el único
  que conoce el secreto (además de los servicios) es el gateway.

### 6.4 Aislamiento de la cuenta de monitoreo

`monitor` vive en su propia cadena de seguridad y **solo sirve para leer los
`/actuator/**` del gateway, config-server y admin-server** (HTTP Basic). No puede
iniciar sesión en `/auth/login` ni obtener un token de usuario.

En los microservicios de negocio, que no tienen Spring Security, la protección de
actuator la da la **exposición acotada** (`health,info,metrics` en un puerto de
management separado) — ver §7.

### 6.5 Los controladores exponen DTOs, no entidades (corrección 5)

Los controladores reciben y devuelven **DTOs con validación Bean Validation**
(`@NotBlank`, `@Email`, etc.), nunca la entidad JPA:

- Inyectar un `id` en el cuerpo no redefine el recurso: se ignora (verificado en las
  pruebas con *mass assignment*).
- Un `POST` sin nombre o con email inválido devuelve **400** antes de tocar la base.

### 6.6 Renovación automática del token (frontend)

`auth.interceptor.ts` renueva el access token con el refresh cuando vence y reintenta la
petición; si el refresh también falla, cierra la sesión. Ver: `frontend/src/app/`.

---

## 7. Configuración centralizada (corrección 3)

El Config Server usa el **backend "git"**: la configuración NO está horneada en el jar,
vive en un repositorio Git versionado y se sirve por HTTP.

- Plantilla versionada: `config-template/` (fuente de verdad).
- Repositorio local: `config-repo/` (repo Git independiente, se crea y sincroniza con
  `.\iniciar-config-repo.ps1`).
- Repositorio **remoto** (privado): `https://github.com/Kaylo777/colegio-config.git`,
  donde el mismo `config-repo` está publicado.

| Archivo | Contenido |
|---|---|
| `application.yml` | Token de servicio, secretos JWT, issuer, expiraciones, RabbitMQ, Eureka, actuator, cuenta `monitor` |
| `gateway.yml` | Puerto y rutas `lb://` (auth, alumnos, administracion) |
| `auth-service.yml` | Puerto 8100, management 9110, usuarios con BCrypt |
| `alumnos.yml` | Puerto 8101, management 9111, datasource H2 |
| `administracion.yml` | Puerto 8102, management 9112, datasource H2 |

Para apuntar al repositorio remoto en vez del local (cualquier servicio que así lo
levante):

```powershell
$env:CONFIG_REPO_URI = 'https://github.com/Kaylo777/colegio-config.git'
```

Cambiar una propiedad = editar, commitear y el Config Server la sirve **sin recompilar**
ni reiniciar. Los hosts (Eureka, Config Server, RabbitMQ) son resolubles por variable de
entorno con default `localhost`, así el mismo repositorio funciona local y en Docker.

Cada servicio lo importa con:

```yaml
spring:
  config:
    import: optional:configserver:http://${CONFIG_SERVER_HOST:localhost}:8888
```

El prefijo `optional:` permite arrancar aunque el Config Server esté caído, usando los
valores de respaldo del `application.yml` local.

---

## 8. RabbitMQ

| Concepto | Valor |
|---|---|
| Exchange | `microservicios.exchange` (TopicExchange) |
| Routing key `alumno.creado` | `alumnos` → cola `microservicios.cola.administracion` (binding `alumno.*`) |
| Routing key `docente.creado` | `administracion` → cola `microservicios.cola.alumnos` (binding `docente.*`) |
| Payload | `EventoNotificacion(String tipo, Long id, String detalle)` |

Flujo: `alumnos` crea un alumno → publica `alumno.creado` → `administracion` lo recibe en
su listener (y al revés con `docente.creado`). La conversión usa
`Jackson2JsonMessageConverter`. Verificado en ejecución por `probar-api.ps1`.

---

## 9. Pruebas realizadas

Existe una batería automatizada, `probar-api.ps1`, de **41 verificaciones** que se ejecuta
contra el stack levantado (local o Docker) y termina en `Total: 41   Fallos: 0`.

La batería cubre:

| Sección | Qué valida |
|---|---|
| Login y perfiles | login admin/user, error 401, `/auth/me`, refresh (token válido/inválido, uso cruzado de tipos) |
| Matriz de roles | la aplica el gateway: `USER` no escribe, sin token no hay nada |
| Token de servicio | 403 directo a 8100/8101/8102 sin cabecera, 200 con el secreto, el `Authorization` del cliente no sirve directo |
| Cuenta `monitor` | health público, `/actuator/env` con/sin Basic, monitor no alcanza para `/api` |
| DTOs | `@NotBlank`, email inválido, mass assignment (el `id` del cuerpo se ignora) |
| Config | la sirve el backend git del Config Server (origen = `config-repo`), trae `app.service-token.value` y `app.jwt.secret` |
| RabbitMQ | evento `alumno.creado` recibido por `administracion` y `docente.creado` por `alumnos` |

Además se verifica que el jar embebido no dependa de archivos externos para las pruebas
y que el frontend compila (`ng build`) y sirve.

---

## 10. Limitaciones conocidas

1. **Los refresh tokens no se revocan.** Son JWT sin estado: si uno se filtra, sirve
   hasta 8 horas. En producción harían falta tokens opacos con rotación y revocación.
2. **Los tokens viven en `localStorage`** (legible desde JS). La alternativa sería una
   cookie `httpOnly` con protección CSRF.
3. **CORS abierto** (`*`). Aceptable para una API con Bearer; en producción se restringe
   a los orígenes del front.
4. **Secreto JWT compartido.** Si un servicio se compromete, puede firmar tokens. La
   alternativa es RS256 por servicio con `kid` y *audience*.
5. **Usuarios en configuración, no en BD.** Es un dato de partida fijo del trabajo.
6. **`prefer-ip-address: true`.** Eureka registra la IP del contenedor/equipo. Dentro de
   Docker esas IPs son de la red interna del compose (correcto para SBA y el load
   balancer), pero no se navegan desde el host por su IP.
7. **El repositorio de configuración es privado.** Para que otra persona corra el
   proyecto "en modo remoto" debe tener acceso a `colegio-config`; la alternativa
   local (montar/`config-repo`) funciona sin credenciales.

---

## 11. Estructura del proyecto

```
colegio-jwt/
├── pom.xml                   POM agregador: compila los 7 microservicios juntos
├── Dockerfile                Imagenes de los 7 microservicios (multi-stage)
├── docker-compose.yml        Stack completo: 7 servicios + rabbitmq + frontend
├── .dockerignore / frontend/.dockerignore
├── config-template/          Plantilla de configuracion (fuente de verdad)
├── config-repo/              Repo Git local servido por el Config Server (.gitignore)
├── backend/
│   ├── gateway/              API Gateway: valida JWT, autoriza, agrega X-Service-Token
│   ├── auth-service/         Identidad: login, emision y renovacion de tokens
│   ├── alumnos/              Microservicio de alumnos (DTOs + ServiceTokenFilter)
│   ├── administracion/       Microservicio de docentes (DTOs + ServiceTokenFilter)
│   ├── registry/             Eureka
│   ├── config-server/        Config Server (backend git)
│   └── admin-server/         Spring Boot Admin
├── frontend/                 Angular (login, alumnos, docentes, guards, interceptor)
│   ├── Dockerfile            Build Node + nginx (SPA + proxy /api y /auth)
│   └── nginx.conf
├── iniciar-backend.ps1       Levanta los 7 servicios en orden, esperando cada puerto
├── detener-backend.ps1       Detiene todo
├── iniciar-config-repo.ps1   Crea/sincroniza config-repo desde config-template
├── probar-api.ps1            Bateria de 41 verificaciones de la API
└── README.md
```

---

## 12. Correspondencia con el enunciado y las correcciones del docente

### Consigna

| Requisito | Dónde se cumple |
|---|---|
| Investigar e implementar Spring Security y JWT | `auth-service` (emisión de tokens), `gateway` (validación y autorización), JJWT 0.12.6 |
| Integrarlo en una arquitectura Spring Cloud | Gateway + Eureka + Config Server + LoadBalancer + Spring Boot Admin + RabbitMQ |
| Front con login en Angular (optativo) | `frontend/src/app/login/`, `auth.guard.ts`, `auth.interceptor.ts`, `auth.service.ts` |
| Entrega en un repositorio | Este repositorio (`Proyecto-escuela` en GitHub), con README, `.gitignore` y scripts |

### Correcciones aplicadas

| Corrección del docente | Cómo se resolvió |
|---|---|
| 1. Spring Security en cada microservicio ("monolitos") | Los microservicios de negocio **no tienen Spring Security**: solo el gateway valida/autoriza (§6.1, §6.3) |
| 2. Gateway manejaba información sensible | Microservicio `auth-service` de identidad + credenciales en el Config Server (§6.1) |
| 3. Config Server en repo remoto o BD | Backend **git**; `config-repo` publicado en `colegio-config` (privado) (§7) |
| 4. Microservicios sin secreto entre sí | Cabecera `X-Service-Token` exigida por `alumnos`/`administracion` → 403 sin ella (§6.3) |
| 5. Entidades en controladores | DTOs con Bean Validation en todos los controladores (§6.5) |
| 6. No dockerizado | `Dockerfile` (7 servicios) + `frontend/Dockerfile` + `docker-compose.yml` completo (§3.1) |