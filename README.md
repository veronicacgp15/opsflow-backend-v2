# OpsFlow Backend - Plataforma SaaS

## Stack tecnológico

- **Lenguaje:** `Java 21`
- **Framework:** `Spring Boot 3.4.x`
- **Seguridad:** `Spring Security 6` + `JWT`
- **Persistencia:** `PostgreSQL 15`
- **Mensajería:** `RabbitMQ 3.12`
- **Caché y refresh tokens:** `Redis 7`
- **Service discovery:** `Netflix Eureka Server`
- **API Gateway:** `Spring Cloud Gateway`

---

## Arquitectura del backend

OpsFlow está compuesto por los siguientes módulos:

| Módulo | Puerto | Responsabilidad | Rutas principales |
| :--- | :---: | :--- | :--- |
| `gateway_service` | `8080` | Punto único de entrada | `/auth/**`, `/users/**`, `/org/**`, `/documents/**` |
| `auth_service` | `8081` | Autenticación, JWT, usuarios, roles y permisos | `/auth/**`, `/users/**`, `/auth-legacy/**` |
| `org_service` | `8082` | Organizaciones y sedes | `/org/**` |
| `document_service` | `8083` | Gestión documental, versiones y descargas | `/documents/**` |
| `eureka_server` | `8761` | Registro y descubrimiento de servicios | Dashboard Eureka |
| `common` | N/A | Librería compartida | Sin endpoints REST |

### Rutas publicadas por el gateway

El acceso recomendado desde clientes externos es a través del gateway en `http://localhost:8080`.

- `/auth/**` -> `auth_service`
- `/users/**` -> `auth_service`
- `/org/**` -> `org_service`
- `/documents/**` -> `document_service`

> **Importante:** los endpoints `/auth-legacy/**` existen en `auth_service`, pero no están publicados explícitamente por `gateway_service`. Para consumirlos debes llamar directamente al puerto `8081`.

---

## Guía de inicio rápido

Sigue estos pasos en orden para levantar el ecosistema completo correctamente.

### 1. Levantar la infraestructura base

Es fundamental que la base de datos `db_opsflow` y los servicios auxiliares estén operativos antes de iniciar los microservicios.

```bash
docker-compose up -d postgres-db redis-cache rabbitmq-broker
```

### 2. Orden de ejecución de microservicios

1. `eureka_server` en `8761`
2. `auth_service` en `8081`
3. `org_service` en `8082`
4. `document_service` en `8083`
5. `gateway_service` en `8080`

### 3. Troubleshooting rápido de puertos en Windows

```bash
# Ver el proceso que ocupa un puerto
netstat -ano | findstr :9000

# Finalizar el proceso
taskkill /F /PID [NUMERO_DE_PID]
```

---

## Monitoreo y estado de los servicios

Para verificar que los servicios se registraron correctamente en Eureka:

- **Dashboard:** [http://localhost:8761/](http://localhost:8761/)
- **Instancias esperadas:**
  - `AUTH-SERVICE`
  - `ORG-SERVICE`
  - `DOCUMENT-SERVICE`
  - `GATEWAY-SERVICE`

Si un servicio no aparece, revisa primero su conectividad hacia la base de datos, Redis o RabbitMQ.

---

## Roles y modelo de autorización

### Roles base del sistema

| Rol | Alcance funcional | Endpoints representativos |
| :--- | :--- | :--- |
| `ROLE_ADMIN` | Acceso global al ecosistema. Administra usuarios, roles, permisos, organizaciones, sedes y operaciones administrativas de documentos. | `/auth/roles/**`, `/auth/permissions`, `/users/**`, `/org/**`, `/documents/{id}/force-state`, `/documents/storage/list` |
| `ROLE_MANAGER` | Administración acotada a su organización. Puede invitar usuarios `ROLE_USER`, consultar usuarios de su organización, actualizar su organización, gestionar sedes propias y operar documentos de su organización. | `/users/my-organization`, `/users/by-organization/{orgId}`, `POST /users`, `PUT /org/{id}`, `/org/locations/**`, `/documents/**` |
| `ROLE_USER` | Rol operativo. Puede autenticarse, cambiar su contraseña, consultar su organización y sus sedes, crear y operar documentos de su organización dentro de las reglas de propiedad. | `/auth/logout`, `/auth/me/permissions`, `/users/change-password`, `/org/{id}`, `/org/locations/by-org/{orgId}`, `/documents/create`, `/documents/{id}` |

### Notas importantes sobre autorización

- Los roles semilla reales del sistema son `ROLE_ADMIN`, `ROLE_MANAGER` y `ROLE_USER`.
- Se pueden crear roles personalizados desde `POST /auth/roles/create`, pero la mayor parte de las reglas de acceso actuales sigue validándose por roles fijos o por lógica de negocio.
- `auth_service` combina reglas de `SecurityConfig` con `@PreAuthorize`.
- `org_service` utiliza reglas dinámicas por organización y, además, permite configurar algunos roles desde `org_service/src/main/resources/application.yml`.
- `document_service` aplica permisos por rol y también valida pertenencia a la organización o propiedad del documento.
- Los endpoints `PATCH /org/{id}/activate` y `PATCH /org/{id}/deactivate` quedan para `ROLE_ADMIN` por defecto, pero también pueden habilitarse mediante las authorities `ORG_ACTIVATE` y `ORG_DEACTIVATE`.

---

## Inventario de endpoints por microservicio

La siguiente matriz refleja los endpoints definidos en el código backend y el rol o condición de acceso efectiva.

### 1. Auth Service

Servicio responsable de autenticación, sesiones, usuarios, roles y permisos.

#### 1.1 Autenticación y sesión

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Login principal | `POST` | `/auth/login` | Público |
| Registro de usuario | `POST` | `/auth/signup` | Público |
| Verificación de correo | `GET` | `/auth/verify?token=...` | Público |
| Solicitud de recuperación de contraseña | `POST` | `/auth/forgot-password` | Público |
| Restablecer contraseña | `POST` | `/auth/reset-password` | Público |
| Refrescar access token | `POST` | `/auth/refresh` | Público con refresh token válido |
| Cerrar sesión | `POST` | `/auth/logout` | Usuario autenticado |
| Ver permisos efectivos del usuario actual | `GET` | `/auth/me/permissions` | Usuario autenticado |
| Login legado | `POST` | `/auth-legacy/login` | Público |
| Registro legado | `POST` | `/auth-legacy/signup` | Público |
| Refresh legado | `POST` | `/auth-legacy/refresh` | Público con refresh token válido |
| Logout legado | `POST` | `/auth-legacy/logout` | Usuario autenticado |

#### 1.2 Roles y permisos

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Listar catálogo de permisos | `GET` | `/auth/permissions` | `ROLE_ADMIN` |
| Listar roles | `GET` | `/auth/roles` | `ROLE_ADMIN` |
| Obtener rol por ID | `GET` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Crear rol | `POST` | `/auth/roles/create` | `ROLE_ADMIN` |
| Actualizar rol | `PUT` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Eliminar rol | `DELETE` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Ver permisos asignados a un rol | `GET` | `/auth/roles/{id}/permissions` | `ROLE_ADMIN` |
| Reemplazar permisos de un rol | `PUT` | `/auth/roles/{id}/permissions` | `ROLE_ADMIN` |
| Reemplazar todos los roles de un usuario por uno solo | `PUT` | `/auth/roles/users/{userId}/change-role` | `ROLE_ADMIN` |
| Reemplazar todos los roles de un usuario por una lista | `PUT` | `/auth/roles/users/{userId}/roles` | `ROLE_ADMIN` |

#### 1.3 Usuarios

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Resolver perfiles públicos por lote | `POST` | `/users/profiles/batch` | Usuario autenticado |
| Listar todos los usuarios | `GET` | `/users` | `ROLE_ADMIN` |
| Listar usuarios de mi organización | `GET` | `/users/my-organization` | `ROLE_ADMIN` o `ROLE_MANAGER` |
| Listar usuarios por organización | `GET` | `/users/by-organization/{orgId}` | `ROLE_ADMIN` o `ROLE_MANAGER` de esa misma organización |
| Obtener usuario por ID | `GET` | `/users/{id}` | `ROLE_ADMIN` |
| Crear o invitar usuario | `POST` | `/users` | `ROLE_ADMIN` o `ROLE_MANAGER` |
| Actualizar usuario | `PUT` | `/users/{id}` | `ROLE_ADMIN` |
| Reemplazar roles del usuario | `PATCH` | `/users/{id}/roles` | `ROLE_ADMIN` |
| Asignar o cambiar manager de una organización | `PUT` | `/users/organizations/{orgId}/manager/{userId}` | `ROLE_ADMIN` |
| Asignar o cambiar manager de una organización | `PATCH` | `/users/organizations/{orgId}/manager/{userId}` | `ROLE_ADMIN` |
| Cambiar mi contraseña | `PATCH` | `/users/change-password` | Usuario autenticado |
| Desactivar usuario | `PATCH` | `/users/{id}/deactivate` | `ROLE_ADMIN` |
| Activar usuario | `PATCH` | `/users/{id}/activate` | `ROLE_ADMIN` |
| Revocar sesión de usuario | `POST` | `/users/{id}/revoke-session` | `ROLE_ADMIN` |
| Generar hash BCrypt | `GET` | `/users/tools/password-hash?password=...` | `ROLE_ADMIN` |

**Reglas adicionales en creación de usuarios (`POST /users`):**

- `ROLE_ADMIN` puede crear usuarios para cualquier organización y con cualquier rol.
- `ROLE_MANAGER` solo puede crear usuarios `ROLE_USER` y únicamente dentro de su propia organización.

### 2. Org Service

Servicio responsable de organizaciones y sedes.

#### 2.1 Organizaciones

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Crear organización | `POST` | `/org/create` | `ROLE_ADMIN` por configuración actual |
| Ver organizaciones del contexto actual | `GET` | `/org/mine` | Usuario autenticado |
| Listar todas las organizaciones | `GET` | `/org` | `ROLE_ADMIN` por configuración actual |
| Obtener organización por ID | `GET` | `/org/{id}` | `ROLE_ADMIN` o usuario miembro de esa organización |
| Actualizar organización | `PUT` | `/org/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` de esa misma organización |
| Eliminar organización | `DELETE` | `/org/{id}` | `ROLE_ADMIN` por configuración actual |
| Activar organización | `PATCH` | `/org/{id}/activate` | `ROLE_ADMIN` por defecto o authority `ORG_ACTIVATE` |
| Desactivar organización | `PATCH` | `/org/{id}/deactivate` | `ROLE_ADMIN` por defecto o authority `ORG_DEACTIVATE` |

**Comportamiento de `GET /org/mine`:**

- `ROLE_ADMIN` obtiene las organizaciones creadas por su `userId`.
- `ROLE_MANAGER` y `ROLE_USER` obtienen su organización asociada en el JWT.

#### 2.2 Sedes (`locations`)

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Crear sede | `POST` | `/org/locations/create` | `ROLE_ADMIN` o `ROLE_MANAGER` de la organización objetivo |
| Listar todas las sedes | `GET` | `/org/locations` | `ROLE_ADMIN` por configuración actual |
| Listar sedes por organización | `GET` | `/org/locations/by-org/{orgId}` | `ROLE_ADMIN` o usuario miembro de esa organización |
| Obtener sede por ID | `GET` | `/org/locations/{id}` | `ROLE_ADMIN` o usuario miembro de la organización propietaria |
| Actualizar sede | `PUT` | `/org/locations/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` de la organización propietaria |
| Eliminar sede | `DELETE` | `/org/locations/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` de la organización propietaria |

### 3. Document Service

Servicio responsable del ciclo de vida documental, versiones y descargas.

| Acción | Método | Endpoint | Acceso |
| :--- | :---: | :--- | :--- |
| Crear documento | `POST` | `/documents/create` | `ROLE_ADMIN`, `ROLE_MANAGER` o `ROLE_USER` |
| Obtener documento por ID | `GET` | `/documents/{id}` | Usuario autenticado con acceso a la organización del documento o `ROLE_ADMIN` |
| Listar documentos | `GET` | `/documents` | Usuario autenticado |
| Actualizar metadatos | `PUT` | `/documents/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER` de la misma organización o dueño del documento |
| Eliminar documento | `DELETE` | `/documents/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` de la misma organización |
| Subir nueva versión | `POST` | `/documents/add-version/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER` de la misma organización o dueño del documento |
| Eliminar una versión | `DELETE` | `/documents/{id}/versions/{versionId}` | `ROLE_ADMIN`, `ROLE_MANAGER` de la misma organización o dueño del documento |
| Forzar cambio de estado | `PATCH` | `/documents/{id}/force-state?state=...` | `ROLE_ADMIN` |
| Descargar última versión | `GET` | `/documents/{id}/download` | Usuario autenticado con acceso a la organización del documento o `ROLE_ADMIN` |
| Descargar versión específica | `GET` | `/documents/{id}/versions/{versionId}/download` | Usuario autenticado con acceso a la organización del documento o `ROLE_ADMIN` |
| Listar archivos del storage | `GET` | `/documents/storage/list?prefix=...` | `ROLE_ADMIN` |
| Listar tipos de documento | `GET` | `/documents/types` | Usuario autenticado |

**Reglas adicionales en documentos:**

- Si el usuario no es `ROLE_ADMIN`, el `organizationId` del documento se fuerza a la organización contenida en el JWT.
- `ROLE_USER` puede actualizar documentos y eliminar versiones solo si es el dueño del documento.
- `ROLE_USER` no puede eliminar el documento completo.
- En `GET /documents`, `ROLE_ADMIN` ve todo; el resto solo los documentos de su organización.

### 4. Módulos sin endpoints de negocio

| Módulo | Descripción |
| :--- | :--- |
| `gateway_service` | No expone endpoints de negocio propios; enruta las llamadas hacia los microservicios. |
| `eureka_server` | Servicio de descubrimiento y registro. |
| `common` | Librería compartida sin controladores REST. |

---

## Swagger / OpenAPI

Swagger está habilitado en los tres microservicios de negocio.

- **Auth Service:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **Org Service:** [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **Document Service:** [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

Rutas públicas relacionadas con documentación:

- `/v3/api-docs`
- `/swagger-ui.html`
- `/swagger-ui/**`

---

## Pruebas con Postman

El repositorio incluye una colección de Postman exportada para probar los endpoints sin configurarlos manualmente.

Recomendación de flujo:

1. Ejecuta login en `/auth/login`.
2. Copia el access token JWT.
3. Envía el token en `Authorization: Bearer <token>`.
4. Prueba los endpoints según el rol del usuario autenticado.

---

## Cobertura y análisis

### JaCoCo

```bash
mvn clean install
```

Luego abre `target/site/jacoco/index.html`.

### SonarQube

1. Levanta el contenedor:

```bash
docker-compose up -d sonarqube
```

2. Accede a [http://localhost:9000](http://localhost:9000) con `admin/admin`.

3. Ejecuta el análisis:

```bash
mvn -DskipTests=true clean compile
mvn sonar:sonar -Dsonar.host.url=http://localhost:9000
```

---

## Limpieza de Docker

```bash
docker-compose down -v
```
