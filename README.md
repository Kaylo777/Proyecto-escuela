# Colegio — Spring Security + JWT sobre Spring Cloud

Trabajo de investigación e implementación de **Spring Security y JWT** integrados en una
arquitectura de **Spring Cloud**, con un frontend **Angular** opcional que incluye login.

El sistema es una aplicación de gestión escolar: microservicios de `alumnos` y
`administracion` (docentes), protegidos por un **API Gateway** que autentica y autoriza
por roles, sobre **Eureka** (registro) y **Config Server** (configuración centralizada),
con **RabbitMQ** para el intercambio de eventos entre servicios.

---

## 1. Arquitectura

```
                        ┌──────────────┐
   Navegador  ────────► │    Angular   │  :4200  (login, alumnos, docentes, seguridad)
                        └──────┬───────┘
                               │  Authorization: Bearer <accessToken>
                               ▼
                        ┌──────────────┐
                        │ API Gateway  │  :8080   ← único punto de entrada público
                        │  autentica   │           valida el token y aplica el rol
                        │  autoriza   │
                        └──────┬───────┘
                               │  balanceo de carga lb://
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
        ┌──────────────────┐        ┌────────────────────┐
        │  Microservicio   │        │   Microservicio    │
        │    alumnos       │ :8101  │  administracion    │ :8102
        │  resource server │        │   resource server  │
        └────────┬─────────┘        └─────────┬──────────┘
                 │  alumnus.creado / docente.creado
                 └────────────┬───────────────┘
                              ▼
                     ┌─────────────────┐
                     │    RabbitMQ     │  :5672  (UI :15672)
                     └─────────────────┘

        Eureka :8761   ·   Config Server :8888   ·   Spring Boot Admin :9090
```

| Servicio | Puerto | Rol |
|---|---|---|
| `gateway` | 8080 | Autentica, emite tokens, autoriza por rol, enruta |
| `alumnos` | 8101 | CRUD de alumnos (H2), publica `alumno.creado` |
| `administracion` | 8102 | CRUD de docentes (H2), publica `docente.creado` |
| `registry` | 8761 | Eureka: registro y descubrimiento de servicios |
| `config-server` | 8888 | Configuración centralizada de todos los servicios |
| `admin-server` | 9090 | Spring Boot Admin: métricas y estado |
| RabbitMQ | 5672 / 15672 | Bus de eventos entre microservicios |
| Angular | 4200 | Frontend con login |

### Stack

| Capa | Tecnología | Versión |
|---|---|---|
| Backend | Spring Boot | 4.0.8 |
| Cloud | Spring Cloud | 2025.1.3 |
| Seguridad | Spring Security + JJWT | 6.x / 0.12.6 |
| Frontend | Angular | 22.1 |
| Base de datos | H2 (archivo) | — |
| Mensajería | RabbitMQ | 3.x (Docker) |
| Build | Maven Wrapper | 3.9.16 |

---

## 2. Requisitos

- **Java 17 o superior** (obligatorio: Spring Boot 4 no compila con Java 11)
- **Node.js 20+** y npm (solo para el frontend)
- **Docker Desktop** (opcional, solo para RabbitMQ)
- **Maven** — no hace falta instalarlo, cada servicio incluye su `mvnw.cmd`

```powershell
java -version     # debe decir 17 o superior
docker --version  # opcional
```

---

## 3. Cómo ejecutarlo

### 3.1 Compilar el backend

Cada microservicio se compila por separado. Desde la raíz:

```powershell
$env:JAVA_HOME = "C:\ruta\a\jdk-21"
cd backend\registry      ; .\mvnw.cmd -DskipTests package
cd ..\config-server      ; .\mvnw.cmd -DskipTests package
cd ..\alumnos            ; .\mvnw.cmd -DskipTests package
cd ..\administracion     ; .\mvnw.cmd -DskipTests package
cd ..\gateway            ; .\mvnw.cmd -DskipTests package
cd ..\admin-server       ; .\mvnw.cmd -DskipTests package
```

O en un bucle:

```powershell
cd backend
foreach ($s in @('registry','config-server','alumnos','administracion','gateway','admin-server')) {
    Push-Location $s ; & ".\mvnw.cmd" -DskipTests -q package ; Pop-Location
}
```

### 3.2 Compilar todo de una vez (con el POM agregador)

En la raíz hay un `pom.xml` agregador con los 6 microservicios como módulos, así que
también alcanza con un solo comando:

```powershell
.\mvnw.cmd -DskipTests package
```

El orden del reactor es `registry` → `config-server` → `alumnos` → `administracion` →
`gateway` → `admin-server`. Tarda unos 15 segundos con las dependencias ya descargadas.

Cada servicio sigue siendo autónomo y se puede compilar por separado (es lo que usa
`iniciar-backend.ps1`), porque cada uno tiene su propio `pom.xml` y su propio `mvnw.cmd`.

### 3.3 Levantar RabbitMQ (opcional)

```powershell
docker compose up -d
docker ps     # debe aparecer rabbitmq-microservicios
```

Sin RabbitMQ los servicios **igual arrancan** y todo lo relativo a JWT y roles funciona
igual; lo único que falla es `POST`/`PUT` de alumnos y docentes, porque el evento no se
puede publicar (ver *Limitaciones conocidas*).

### 3.4 Levantar el backend

```powershell
.\iniciar-backend.ps1
```

Arranca en orden: `registry` → `config-server` → `alumnos` → `administracion` →
`gateway` → `admin-server`, **esperando a que cada puerto abra** antes de seguir.
Los logs quedan en `backend\logs\`.

Para detener todo:

```powershell
.\detener-backend.ps1
```

### 3.5 Levantar el frontend

```powershell
cd frontend
npm install
npm start          # http://localhost:4200
```

### 3.6 Trabajar en Eclipse

El backend se puede abrir completo desde Eclipse, sin importar servicio por servicio,
porque la raíz tiene un POM agregador.

1. Abrí la carpeta raíz del proyecto en Eclipse.
2. `File` → `New` → `Maven Project` → **Existing Maven Projects**.
3. Seleccioná el `pom.xml` de la **raíz** (no las carpetas de cada servicio).
4. Finish: aparecen los 6 microservicios como proyectos Maven.
5. `Project` → `Update Maven Project` (Ctrl+Shift+O) para resolver dependencias.

Requisitos: JDK 17 o superior configurado en `Window` → `Preferences` → `Java` →
`Installed JREs`.

Cada microservicio es un proyecto Maven independiente, así que también se puede hacer
`Run As` → `Spring Boot App` sobre cualquiera de ellos. Para levantarlos todos con el
orden y las esperas correctas conviene usar `.\iniciar-backend.ps1` desde PowerShell
(usando los `target\*.jar` ya compilados), no desde Eclipse.

Eclipse genera `.project`, `.classpath` y `.settings/`, que están en `.gitignore` a
propósito: son específicos de cada máquina y si se commitean generan conflictos.

### 3.7 Trabajar en Visual Studio Code

El repo trae `.vscode/extensions.json` y `.vscode/settings.json` compartidos. Al abrir
la carpeta en VS Code aparece una notificación para instalar:

- **Extension Pack for Java** (incluye Maven for Java, que reconoce el POM agregador)
- **Angular Language Service**, para el front

En `.vscode/launch.json` se pueden agregar configuraciones de depuración para correr
cada microservicio. El front se levanta desde la terminal integrada con `npm start`.

---

## 4. Usuarios de prueba

Definidos en el Config Server (`backend\config-server\src\main\resources\config\gateway.yml`),
con contraseñas hasheadas en **BCrypt** (`{bcrypt}...`); nunca en texto plano.

| Usuario | Contraseña | Rol | Puede |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | Leer **y** escribir, ver actuator |
| `user` | `user123` | `USER` | Solo leer (`GET`) |
| `monitor` | `monitor123` | `ADMIN` | Cuenta de servicio, **solo** `/actuator/**` |

---

## 5. Endpoints y matriz de roles

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
| cualquier otra ruta | 403 | 403 | 403 (`denyAll`) |

---

## 6. Cómo funciona la seguridad

### 6.1 Login y emisión de tokens

```
Usuario → POST /auth/login → Gateway
                              ├─ valida usuario/contraseña contra BCrypt
                              ├─ firma access token  (HS256, 15 min)
                              └─ firma refresh token (HS256, 8 h)
Angular ← guarda ambos en localStorage
```

Después de cada login, Angular manda el access token en la cabecera
`Authorization: Bearer <token>`.

### 6.2 Dos secretos distintos

`app.jwt.secret` firma los **access tokens** y `app.jwt.refresh-secret` firma los
**refresh tokens**. Al usar claves diferentes, un refresh token no puede usarse como
access token ni al revés: cada tipo solo valida contra su propia clave. Verificado en
*Pruebas realizadas*.

### 6.3 Defensa en profundidad: el gateway no es el único que valida

El gateway es el **autorizador**, pero los microservicios son **resource servers** que
validan el mismo token por su cuenta, con el mismo secreto y el mismo emisor
(`colegio-microservicios`). Esto evita que alguien esquive el gateway entrando
directamente al puerto 8101/8102 con un token inválido, y también que un token emitido
para otra aplicación sea aceptado.

Hay además `@PreAuthorize("hasRole('ADMIN')")` en los controladores, como tercera capa.

### 6.4 Renovación automática del token

`auth.interceptor.ts` (Angular):

1. Si el access token ya venció, pide uno nuevo con el refresh token **antes** de enviar.
2. Si la API responde 401, reintenta la petición una sola vez con el token renovado.
3. Si el refresh también falla, cierra la sesión y vuelve al login.

### 6.5 Aislamiento de la cuenta de monitoreo

`monitor` vive en un `authenticationManager` **separado** y en una cadena de seguridad
**exclusiva de `/actuator/**`** (`@Order(1)` + `securityMatcher`). Consecuencias:

- No se puede usar para iniciar sesión en `/auth/login` ni obtener un token de usuario.
- Un `Authorization: Basic monitor:...` **no** autoriza nada fuera de `/actuator/**`.

---

## 7. Configuración centralizada

El Config Server (perfil `native`, `:8888`) sirve la configuración desde
`backend\config-server\src\main\resources\config\`:

| Archivo | Contenido |
|---|---|
| `application.yml` | Secreto JWT, issuer, expiraciones, RabbitMQ, Eureka, actuator, cuenta `monitor` |
| `gateway.yml` | Puerto, usuarios con BCrypt, rutas `lb://`, CORS |
| `alumnos.yml` | Puerto 8101, datasource H2 |
| `administracion.yml` | Puerto 8102, datasource H2 |

Cada servicio lo importa con:

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
```

El prefijo `optional:` permite que un servicio arranque aunque el Config Server esté
caído, usando los valores de respaldo de su `application.yml` local.

---

## 8. RabbitMQ

| Concepto | Valor |
|---|---|
| Exchange | `microservicios.exchange` (TopicExchange) |
| Routing key `alumno.creado` | `alumnos` → cola `microservicios.cola.administracion` (binding `alumno.*`) |
| Routing key `docente.creado` | `administracion` → cola `microservicios.cola.alumnos` (binding `docente.*`) |
| Payload | `EventoNotificacion(String tipo, Long id, String detalle)` |

Flujo: `alumnos` crea un alumno → publica `alumno.creado` → `administracion` lo recibe en
su listener. La conversión del mensaje usa `Jackson2JsonMessageConverter`.

Verificado en ejecución (ver *Pruebas realizadas*): al crear un alumno por la API,
`alumnos` publica el evento y `administracion` lo recibe, y al revés al crear un docente.

---

## 9. Pruebas realizadas

Levantado el stack completo (con RabbitMQ), se ejecutó esta matriz contra el gateway.
**Todas las pruebas pasaron.**

### Seguridad y roles

| # | Prueba | Esperado | Resultado |
|---|---|---|---|
| 1 | `POST /auth/login` admin/admin123 | 200 + token 900s | OK |
| 2 | `POST /auth/login` user/user123 | 200 + rol `USER` | OK |
| 3 | `POST /auth/login` contraseña incorrecta | 401 | OK |
| 4 | `GET /auth/me` con token | 200 | OK |
| 5 | `GET /auth/me` sin token | 401 | OK |
| 6 | `GET /api/alumnos` con admin | 200 | OK |
| 7 | `GET /api/alumnos` con user | 200 | OK |
| 8 | `GET /api/alumnos` sin token | 401 | OK |
| 9 | `POST /api/alumnos` con user | 403 | OK |
| 10 | `POST /api/alumnos` sin token | 401 | OK |
| 11 | `GET /actuator/health` sin token | 200 | OK |
| 12 | `GET /actuator/env` Basic `monitor` correcto | 200 | OK |
| 13 | `GET /actuator/env` Basic `monitor` incorrecto | 401 | OK |
| 14 | `GET /actuator/env` sin credencial | 401 | OK |
| 15 | Basic `monitor` sobre `/api/alumnos` | 401 | OK |
| 16 | `POST /auth/refresh` token inválido | 401 | OK |
| 17 | Refresh token usado como Bearer en `/api` | 401 | OK |
| 18 | Access token usado en `/auth/refresh` | 401 | OK |
| 19 | `POST /auth/refresh` con refresh válido | 200 + token nuevo | OK |
| 20 | Ruta inexistente con token válido | 403 | OK |

Las pruebas 17 y 18 son las que confirman que los dos secretos separadas cumplen su
función: un token de un tipo no sirve como credencial del otro.

### Bypass del gateway

| # | Prueba | Esperado | Resultado |
|---|---|---|---|
| 21 | `POST` directo a `:8101` con user | 403 | OK |
| 22 | `GET` directo a `:8101` con user | 200 | OK |
| 23 | `GET` directo a `:8101` sin token | 401 | OK |

Estas tres son las más importantes del trabajo: confirman que **saltarse el gateway no
sirve**, porque el microservicio revalida el token por su cuenta.

### CRUD y mensajería

| # | Prueba | Esperado | Resultado |
|---|---|---|---|
| 24 | `POST /api/alumnos` con admin | 201 | OK |
| 25 | `POST /api/administracion` con admin | 201 | OK |
| 26 | `GET /api/administracion` con admin / user | 200 | OK |
| 27 | Evento `alumno.creado` publicado por `alumnos` | publicado | OK |
| 28 | Evento `alumno.creado` recibido por `administracion` | recibido | OK |
| 29 | Evento `docente.creado` publicado por `administracion` | publicado | OK |
| 30 | Evento `docente.creado` recibido por `alumnos` | recibido | OK |

Topología confirmada en el broker: exchange `microservicios.exchange` (topic) con las
colas `microservicios.cola.alumnos` y `microservicios.cola.administracion`, un consumidor
activo en cada una, y los bindings `alumno.*` y `docente.*`.

Además se verificó que los tres hashes BCrypt de la configuración
(`admin123`, `user123`, `monitor123`) corresponden realmente a esas contraseñas, y que
el frontend compila (`ng build`) y sirve (`ng serve` → 200 en `/` y `/main.js`).

---

## 10. Limitaciones conocidas

Puntos a tener en cuenta, asumidos conscientemente para este trabajo:

1. **Sin Docker, `POST`/`PUT` devuelven 500.** El evento se publica de forma síncrona y
   si RabbitMQ no está, `AmqpConnectException` corta la respuesta. El `GET` y toda la
   parte de JWT funcionan igual. Con `docker compose up -d` se resuelve (es lo que
   happens con el stack completo, ver *Pruebas realizadas*). En producción el patrón
   correcto sería una *outbox* transaccional.
2. **Los refresh tokens no se revocan.** Son JWT sin estado: si uno se filtra, sirve
   hasta 8 horas. En producción harían falta tokens opacos guardados en base de datos
   con rotación y revocación explícita.
3. **Los tokens viven en `localStorage`**, legible desde JavaScript. La alternativa
   sería una cookie `httpOnly`, que obliga a trabajar con protección CSRF.
4. **CORS abierto a cualquier origen** (`*`). Aceptable para una API con token Bearer,
   pero en producción se restringe a los orígenes del front.
5. **Secreto JWT compartido por los microservicios.** Si uno se compromete, se pueden
   firmar tokens para todos. La alternativa es un par de claves por servicio (RS256 con
   `kid`) y validación de *audience*.
6. **Los puertos 8101/8102 están expuestos.** Están protegidos, pero en producción
   irían detrás del gateway en una red interna.
7. **Usuarios en configuración, no en base de datos.** Es un dato de partida fijo.
8. **`eureka.instance.prefer-ip-address: true`.** Sin esto, Eureka publicaba el nombre
   del equipo (por ejemplo `KAYLO777.mshome.net`), el load balancer del gateway no lo
   resolvía y todo `/api/**` respondía 500 con    `NXDOMAIN`. Se registra la IP para evitarlo,
   y de paso que un adaptador virtual (WSL, Docker, Hyper-V) no sea elegido por error.

---

## 11. Estructura del proyecto

```
colegio-jwt/
├── pom.xml                   POM agregador: compila los 6 microservicios juntos
├── backend/
│   ├── gateway/            API Gateway: login, JWT, roles, rutas
│   ├── alumnos/            Microservicio de alumnos
│   ├── administracion/     Microservicio de docentes
│   ├── registry/           Eureka
│   ├── config-server/      Configuración centralizada (+ archivos en resources/config)
│   └── admin-server/       Spring Boot Admin
├── frontend/               Angular: login, alumnos, docentes, panel de seguridad
├── .vscode/                Configuración compartida de Visual Studio Code
├── iniciar-backend.ps1     Levanta los 6 servicios en orden, esperando cada puerto
├── detener-backend.ps1     Detiene todo
├── docker-compose.yml      RabbitMQ
└── README.md
```

---

## 12. Correspondencia con el enunciado

| Requisito | Dónde se cumple |
|---|---|
| Investigar e implementar Spring Security y JWT | `backend/gateway/.../security/` (emisión y validación), `backend/{alumnos,administracion}/.../security/` (resource servers) |
| Integrarlo en una arquitectura Spring Cloud | Gateway + Eureka + Config Server + Spring Cloud LoadBalancer + Spring Boot Admin |
| Front con login en Angular (optativo) | `frontend/src/app/login/`, `auth.guard.ts`, `auth.interceptor.ts`, `services/auth.service.ts` |
| Entrega en un repositorio | Este repositorio, con README, `.gitignore` y scripts de arranque |
