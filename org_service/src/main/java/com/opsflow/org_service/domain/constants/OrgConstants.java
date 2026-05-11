package com.opsflow.org_service.domain.constants;

public final class OrgConstants {

    public static final String ORG_SERVICE_EVENTO_DE_USUARIO_REGISTRADO_RECIBIDO = "Org Service: Evento de usuario registrado recibido: ";
    public static final String ERROR_AL_ASOCIAR_USUARIO_A_ORGANIZACION = "Error al asociar usuario a organización: ";
    public static final String ORGANIZATION_NOT_FOUND_WITH_ID = "Organization not found with ID: ";
    public static final String ASOCIADO_A_LA_ORGANIZACION = " asociado a la organización ";
    public static final String ERROR_AUTENTICACION_DEL_USUARIO = "No se pudo establecer la autenticación del usuario: {}";
    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER = "Bearer";

    private OrgConstants() {}

    public static final String OPSFLOW_EXCHANGE = "opsflow.exchange";
    public static final String AUTH_USER_REGISTERED_QUEUE = "auth.user.registered";
    public static final String AUTH_USER_ROUTING_KEY = "auth.user.#";


}
