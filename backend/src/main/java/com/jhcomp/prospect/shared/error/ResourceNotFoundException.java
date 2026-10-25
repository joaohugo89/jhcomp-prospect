package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * Recurso inexistente ou fora da organizacao do solicitante.
 *
 * <p>Um recurso de outra organizacao responde 404, nao 403: informar que o
 * recurso existe mas pertence a outra organizacao ja e vazamento de
 * informacao.
 */
public class ResourceNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resourceType) {
        super(ApiErrorType.RESOURCE_NOT_FOUND, 404, resourceType + " not found");
    }
}
