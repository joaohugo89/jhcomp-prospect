package com.jhcomp.prospect.lead.presentation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jhcomp.prospect.lead.application.LeadViewService;
import com.jhcomp.prospect.lead.infrastructure.persistence.LeadViewEntity;
import com.jhcomp.prospect.shared.web.InputPatterns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Visoes salvas da lista de leads (D31). No maximo
 * {@value LeadViewService#MAX_VIEWS_PER_USER} por pessoa; lista sem paginacao
 * por isso.
 */
@RestController
@RequestMapping("/leads/views")
public class LeadViewController {

    private final LeadViewService service;

    LeadViewController(LeadViewService service) {
        this.service = service;
    }

    /**
     * {@code query} e a query string da lista ja codificada: so os caracteres
     * de uma URL, sem espaco nem controle. A tela so aplica os filtros que conhece.
     */
    public record CreateViewRequest(
            @NotBlank @Size(max = 60) @Pattern(regexp = InputPatterns.NOT_BLANK_WHEN_PRESENT) String name,
            @NotNull @Size(max = 500) @Pattern(regexp = "^[A-Za-z0-9_.~%=&+-]*$", message = "must be a query string")
                    String query) {}

    public record ViewResponse(UUID id, String name, String query, Instant createdAt) {
        static ViewResponse from(LeadViewEntity view) {
            return new ViewResponse(view.getId(), view.getName(), view.getQuery(), view.getCreatedAt());
        }
    }

    @GetMapping
    public List<ViewResponse> mine() {
        return service.mine().stream().map(ViewResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ViewResponse create(@Valid @RequestBody CreateViewRequest request) {
        return ViewResponse.from(service.create(request.name(), request.query()));
    }

    @DeleteMapping("/{viewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID viewId) {
        service.delete(viewId);
    }
}
