package com.jhcomp.prospect.shared.persistence;

import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;

/**
 * Base das entidades com id gerado pela aplicacao.
 *
 * <p>Com id ja preenchido, o Spring Data nao distingue entidade nova de
 * existente e trata todo {@code save} como possivel atualizacao: faz um
 * {@code SELECT} antes de cada {@code INSERT} e devolve uma copia em vez da
 * instancia gravada. Numa importacao de duas mil linhas com leads, eram cerca
 * de dez mil consultas a mais. Marcar a entidade como nova ate ela ser gravada
 * ou carregada leva direto ao {@code INSERT}.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    protected AssignedIdEntity() {
        // Exigido pelo JPA.
    }

    protected AssignedIdEntity(UUID id) {
        this.id = id;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        this.isNew = false;
    }
}
