package com.jhcomp.prospect.shared.audit;

/** Acoes auditadas (doc 07 §11). Novas etapas acrescentam valores aqui. */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    IMPORT_CONFIRM,
    IMPORT_DISCARD,
    SUPPRESSION_CREATE,
    MESSAGE_APPROVE,
    MESSAGE_SEND_RECORD
}
