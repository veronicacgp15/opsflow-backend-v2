# 🚀 OpsFlow Backend - Plataforma SaaS

## 🛠 Stack Tecnológico (Core)

- **Lenguaje:** `Java 21`
- **Framework:** `Spring Boot 3.4.x` (Alineado con Spring Cloud 2024.0.0)
- **Seguridad:** `Spring Security 6` + `JWT` (JSON Web Tokens)
- **Persistencia:** `PostgreSQL 15`
- **Mensajería:** `RabbitMQ 3.12` (Arquitectura Event-Driven)
- **Caché & Tokens:** `Redis 7` (Gestión de Refresh Tokens)
- **Descubrimiento:** `Netflix Eureka Server`
- **Gateway:** `Spring Cloud Gateway`

---

## 🚀 Guía de Inicio Rápido

Siga estos pasos en orden para levantar el ecosistema completo correctamente.

### 1. Levantar la Infraestructura (Docker) 🐳

Es **fundamental** que la base de datos esté creada (`db_opsflow`) y operativa antes de ejecutar los microservicios.
Asegúrate de tener Docker instalado y ejecuta los siguientes comandos desde la raíz del proyecto:

```bash
# Copia en el terminal 
docker-compose up -d postgres-db redis-cache rabbitmq-broker
```

### 2. Orden de Ejecución de Microservicios ⚙️

1.  **🌐 Eureka Server**: Puerto `8761`.
2.  **🔐 Auth Service**: Puerto `8081`.
3.  **🏢 Org Service**: Puerto `8082`.
4.  **📄 Document Service**: Puerto `8083`.
5.  **🚪 Gateway Service**: Puerto `8080`.
---
    💡 Nota (Troubleshooting): ¿Un puerto ya está en uso?
    Si al intentar levantar un microservicio (msc) obtienes un error porque el puerto ya está ocupado, puedes liberarlo desde la terminal de Windows.
```bash
# 1. Ver el proceso que ocupa el puerto (ejemplo con el puerto 9000)
netstat -ano | findstr :9000

# 2. Matar el proceso (Reemplaza [NÚMERO_DE_PID] por el número de la última columna del comando anterior)
taskkill /F /PID [NÚMERO_DE_PID]
```
---
## 🔍 Monitoreo y Estado de los Servicios (Service Discovery)

Para verificar qué microservicios se han registrado correctamente y están operativos, puedes acceder al **Dashboard de Eureka**. Es la forma más rápida de confirmar que el ecosistema está "saludable" sin revisar logs individuales.

* **Panel de Control:** [http://localhost:8761/](http://localhost:8761/)
* **Qué buscar:** En la sección *"Instances currently registered with Eureka"*, deberías ver listados:
    * `AUTH-SERVICE`
    * `ORG-SERVICE`
    * `DOCUMENT-SERVICE`
    * `GATEWAY-SERVICE`

> **Nota:** Si un servicio no aparece en la lista, revisa que haya podido conectar con la base de datos o el broker de mensajería primero.

## 🧪 Pruebas con Postman / Swagger (Paso a Paso)
📦 Colección de Postman: El repositorio incluye una colección de Postman exportada lista para importar. Puedes utilizarla para probar todos los endpoints sin necesidad de configurar las peticiones manualmente.

### A. Autenticación, Roles y Usuarios (Auth Service)

| Acción | Método | Endpoint | Permiso |
| :--- | :--- | :--- | :--- |
| Registro de Usuario | `POST` | `/auth/signup` | Público |
| Login | `POST` | `/auth/login` | Público |
| Refrescar Access Token | `POST` | `/auth/refresh` | Público (con refresh token válido) |
| Logout | `POST` | `/auth/logout` | Usuario autenticado |
| Generar Hash (utilidad) | `GET` | `/auth/generate-hash` | Público |
| Listar Roles | `GET` | `/auth/roles` | `ROLE_ADMIN` |
| Obtener Rol por ID | `GET` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Crear Rol | `POST` | `/auth/roles/create` | `ROLE_ADMIN` |
| Actualizar Rol | `PUT` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Eliminar Rol | `DELETE` | `/auth/roles/{id}` | `ROLE_ADMIN` |
| Cambiar Rol a Usuario | `PUT` | `/auth/roles/users/{userId}/change-role` | `ROLE_ADMIN` |
| Listar Usuarios | `GET` | `/users` | `ROLE_ADMIN` |
| Obtener Usuario por ID | `GET` | `/users/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` (misma organización) |
| Usuarios de mi Organización | `GET` | `/users/my-organization` | `ROLE_MANAGER` |
| Crear Usuario | `POST` | `/users` | `ROLE_ADMIN` o `ROLE_MANAGER` |
| Actualizar Usuario | `PUT` | `/users/{id}` | `ROLE_ADMIN` |
| Actualizar Roles de Usuario | `PATCH` | `/users/{id}/roles` | `ROLE_ADMIN` |
| Desactivar Usuario | `PATCH` | `/users/{id}/deactivate` | `ROLE_ADMIN` |
| Revocar Sesiones de Usuario | `DELETE` | `/users/{id}/sessions` | `ROLE_ADMIN` |
| Cambiar mi Contraseña | `PATCH` | `/users/change-password` | Usuario autenticado |

### B. Gestión de Organizaciones y Sedes (Org Service)

| Acción | Método | Endpoint | Permiso |
| :--- | :--- | :--- | :--- |
| Crear Organización | `POST` | `/org/create` | `ROLE_ADMIN` |
| Listar Organizaciones | `GET` | `/org` | `ROLE_ADMIN` |
| Obtener Organización por ID | `GET` | `/org/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER`/`ROLE_USER` (si pertenece) |
| Actualizar Organización | `PUT` | `/org/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` (si pertenece) |
| Eliminar Organización | `DELETE` | `/org/{id}` | `ROLE_ADMIN` |
| Crear Sede | `POST` | `/org/locations/create` | `ROLE_ADMIN` |
| Listar Sedes | `GET` | `/org/locations` | `ROLE_ADMIN` |
| Listar Sedes por Organización | `GET` | `/org/locations/by-org/{orgId}` | `ROLE_ADMIN`, `ROLE_MANAGER`/`ROLE_USER` (si pertenece) |
| Obtener Sede por ID | `GET` | `/org/locations/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER`/`ROLE_USER` (misma organización) |
| Actualizar Sede | `PUT` | `/org/locations/{id}` | `ROLE_ADMIN` |
| Eliminar Sede | `DELETE` | `/org/locations/{id}` | `ROLE_ADMIN` |

### C. Gestión Documental (Document Service)

| Acción | Método | Endpoint | Permiso |
| :--- | :--- | :--- | :--- |
| Crear Documento | `POST` | `/documents/create` | `ROLE_ADMIN`, `ROLE_MANAGER`, `ROLE_USER` |
| Listar Documentos | `GET` | `/documents` | Usuario autenticado |
| Buscar Documento por ID | `GET` | `/documents/{id}` | Según regla `canAccessDocument` |
| Actualizar Metadatos | `PUT` | `/documents/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER` (misma organización) o dueño |
| Eliminar Documento | `DELETE` | `/documents/{id}` | `ROLE_ADMIN` o `ROLE_MANAGER` (misma organización) |
| Subir Nueva Versión | `POST` | `/documents/add-version/{id}` | `ROLE_ADMIN`, `ROLE_MANAGER` (misma organización) o dueño |
| Forzar Estado | `PATCH` | `/documents/{id}/force-state` | `ROLE_ADMIN` |

---

## 📖 Swagger/OpenAPI (verificado)

Swagger está configurado y habilitado en los tres microservicios:

- Dependencia en `pom.xml`: `org.springdoc:springdoc-openapi-starter-webmvc-ui`
- Configuración en `application.yml`: `springdoc.api-docs.path=/v3/api-docs` y `springdoc.swagger-ui.path=/swagger-ui.html`
- Rutas Swagger permitidas en seguridad (`/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`)

URLs:

- **Auth Service:** [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **Org Service:** [http://localhost:8082/swagger-ui.html](http://localhost:8082/swagger-ui.html)
- **Document Service:** [http://localhost:8083/swagger-ui.html](http://localhost:8083/swagger-ui.html)

---
## 📊 Cobertura y Análisis (JaCoCo & SonarQube)
JaCoCo

Ejecuta mvn clean verify y abre target/site/jacoco/index.html.
```bash
mvn clean install
```
SonarQube
- 1.Levanta el contenedor: docker-compose up -d sonarqube
- 2.Accede a http://localhost:9000 (admin/admin).
  ```bash
    #Configuracion levantar el proyecto en sonar ir a
    Administration > Secutiry > deshabiltar los 3
    Secutiry > Global permission > selecciona todas
  ```
- 3.Ejecuta el análisis
  ```bash
    #Activacion
      docker-compose up -d sonarqube
  
    #Activacion de Test
      mvn -DskipTests=true clean compile 
  
      mvn sonar:sonar -Dsonar.host.url=http://localhost:9000
  ```

## 📦 Limpieza de Docker

```bash
docker-compose down -v
```
