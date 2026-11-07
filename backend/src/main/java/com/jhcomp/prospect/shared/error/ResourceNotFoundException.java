package com.jhcomp.prospect.shared.error;

import com.jhcomp.prospect.shared.web.ApiErrorType;

/**
 * A resource that does not exist, or lies outside the caller's organisation.
 *
 * <p>A resource from another organisation answers 404, not 403: saying that
 * it exists but belongs to another organisation is already an information
 * leak.
 */
public class ResourceNotFoundException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String resourceType) {
        super(ApiErrorType.RESOURCE_NOT_FOUND, 404, resourceType + " not found");
    }
}
