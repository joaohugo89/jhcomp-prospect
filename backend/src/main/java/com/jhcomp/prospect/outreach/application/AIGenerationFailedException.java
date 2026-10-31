package com.jhcomp.prospect.outreach.application;

import com.jhcomp.prospect.shared.error.ApplicationException;
import com.jhcomp.prospect.shared.web.ApiErrorType;

/** Provedor indisponivel ou saida recusada pela validacao: nenhum rascunho foi gravado. */
public class AIGenerationFailedException extends ApplicationException {

    private static final long serialVersionUID = 1L;

    public AIGenerationFailedException() {
        super(ApiErrorType.AI_PROVIDER_ERROR, 502, "The message could not be generated. Try again later");
    }
}
