package com.opsflow.auth_service.domain.constants;

public enum SystemPermission {


    AUTH_LOGOUT("auth", "POST", "/auth/logout", "Cerrar sesion del usuario actual"),

    AUTH_ROLES_LIST("auth", "GET", "/auth/roles", "Listar todos los roles"),
    AUTH_ROLES_GET("auth", "GET", "/auth/roles/{id}", "Obtener un rol por ID"),
    AUTH_ROLES_CREATE("auth", "POST", "/auth/roles/create", "Crear un nuevo rol"),
    AUTH_ROLES_UPDATE("auth", "PUT", "/auth/roles/{id}", "Actualizar un rol"),
    AUTH_ROLES_DELETE("auth", "DELETE", "/auth/roles/{id}", "Eliminar un rol"),
    AUTH_ROLES_CHANGE_USER("auth", "PUT", "/auth/roles/users/{userId}/change-role",
            "Cambiar el rol asignado a un usuario"),

    // -------------------------------------------------------------------------
    // AUTH SERVICE — gestion de permisos por rol (esta misma feature)
    // -------------------------------------------------------------------------
    AUTH_PERMISSIONS_LIST("auth", "GET", "/auth/permissions",
            "Listar el catalogo completo de permisos"),
    AUTH_ROLES_PERMISSIONS_GET("auth", "GET", "/auth/roles/{id}/permissions",
            "Obtener los permisos asignados a un rol"),
    AUTH_ROLES_PERMISSIONS_SET("auth", "PUT", "/auth/roles/{id}/permissions",
            "Asignar la lista de permisos de un rol"),

    // -------------------------------------------------------------------------
    // AUTH SERVICE — gestion de usuarios
    // -------------------------------------------------------------------------
    USERS_LIST("auth", "GET", "/users", "Listar todos los usuarios"),
    USERS_GET("auth", "GET", "/users/{id}", "Obtener un usuario por ID"),
    USERS_MY_ORGANIZATION("auth", "GET", "/users/my-organization",
            "Listar usuarios de mi organizacion"),
    USERS_CREATE("auth", "POST", "/users", "Crear un nuevo usuario"),
    USERS_UPDATE("auth", "PUT", "/users/{id}", "Actualizar un usuario"),
    USERS_UPDATE_ROLES("auth", "PATCH", "/users/{id}/roles", "Actualizar los roles de un usuario"),
    USERS_DEACTIVATE("auth", "PATCH", "/users/{id}/deactivate", "Desactivar un usuario"),
    USERS_ACTIVATE("auth", "PATCH", "/users/{id}/activate", "Activar un usuario"),
    USERS_REVOKE_SESSIONS("auth", "POST", "/users/{id}/revoke-session",
            "Revocar la sesion activa de un usuario (elimina su refresh token)"),
    USERS_CHANGE_PASSWORD("auth", "PATCH", "/users/change-password",
            "Cambiar mi propia contrasena"),
    USERS_PROFILES_BATCH("auth", "POST", "/users/profiles/batch",
            "Resolver nombre+apellido de varios usuarios por id (uso interno)"),

    // -------------------------------------------------------------------------
    // ORG SERVICE — organizaciones
    // -------------------------------------------------------------------------
    ORG_CREATE("org", "POST", "/org/create", "Crear una organizacion"),
    ORG_LIST("org", "GET", "/org", "Listar todas las organizaciones"),
    ORG_GET("org", "GET", "/org/{id}", "Obtener una organizacion por ID"),
    ORG_UPDATE("org", "PUT", "/org/{id}", "Actualizar una organizacion"),
    ORG_DELETE("org", "DELETE", "/org/{id}", "Eliminar una organizacion"),
    ORG_ACTIVATE("org", "PATCH", "/org/{id}/activate", "Activar una organizacion"),
    ORG_DEACTIVATE("org", "PATCH", "/org/{id}/deactivate", "Desactivar una organizacion"),

    // -------------------------------------------------------------------------
    // ORG SERVICE — sedes
    // -------------------------------------------------------------------------
    LOCATION_CREATE("org", "POST", "/org/locations/create", "Crear una sede"),
    LOCATION_LIST("org", "GET", "/org/locations", "Listar todas las sedes"),
    LOCATION_BY_ORG("org", "GET", "/org/locations/by-org/{orgId}",
            "Listar sedes de una organizacion"),
    LOCATION_GET("org", "GET", "/org/locations/{id}", "Obtener una sede por ID"),
    LOCATION_UPDATE("org", "PUT", "/org/locations/{id}", "Actualizar una sede"),
    LOCATION_DELETE("org", "DELETE", "/org/locations/{id}", "Eliminar una sede"),

    // -------------------------------------------------------------------------
    // DOCUMENT SERVICE
    // -------------------------------------------------------------------------
    DOC_CREATE("document", "POST", "/documents/create", "Crear un documento"),
    DOC_LIST("document", "GET", "/documents", "Listar documentos"),
    DOC_GET("document", "GET", "/documents/{id}", "Obtener un documento por ID"),
    DOC_UPDATE("document", "PUT", "/documents/{id}", "Actualizar metadatos de documento"),
    DOC_DELETE("document", "DELETE", "/documents/{id}", "Eliminar un documento"),
    DOC_ADD_VERSION("document", "POST", "/documents/add-version/{id}",
            "Subir una nueva version del documento"),
    DOC_FORCE_STATE("document", "PATCH", "/documents/{id}/force-state",
            "Forzar el estado de un documento"),
    DOC_TYPES_LIST("document", "GET", "/documents/types",
            "Listar el catalogo de tipos de documento"),
    DOC_DOWNLOAD("document", "GET", "/documents/{id}/download",
            "Descargar/visualizar el archivo de un documento"),
    DOC_STORAGE_LIST("document", "GET", "/documents/storage/list",
            "Listar archivos del bucket (Cloudflare R2 o disco local)");

    private final String service;
    private final String httpMethod;
    private final String urlPattern;
    private final String description;

    SystemPermission(String service, String httpMethod, String urlPattern, String description) {
        this.service = service;
        this.httpMethod = httpMethod;
        this.urlPattern = urlPattern;
        this.description = description;
    }

    public String code() {
        return name();
    }

    public String service() {
        return service;
    }

    public String httpMethod() {
        return httpMethod;
    }

    public String urlPattern() {
        return urlPattern;
    }

    public String description() {
        return description;
    }
}
