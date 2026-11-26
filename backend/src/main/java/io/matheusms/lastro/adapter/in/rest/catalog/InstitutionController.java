package io.matheusms.lastro.adapter.in.rest.catalog;

import io.matheusms.lastro.application.InstitutionService;
import io.matheusms.lastro.domain.model.Cnpj;
import io.matheusms.lastro.domain.model.Institution;
import io.matheusms.lastro.domain.model.InstitutionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/institutions")
class InstitutionController {

    private final InstitutionService institutions;

    InstitutionController(InstitutionService institutions) {
        this.institutions = institutions;
    }

    @GetMapping
    List<InstitutionResponse> list() {
        return institutions.list().stream().map(InstitutionResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InstitutionResponse create(@Valid @RequestBody InstitutionRequest request) {
        var cnpj = request.cnpj() == null || request.cnpj().isBlank() ? null : new Cnpj(request.cnpj());
        return InstitutionResponse.from(institutions.create(new Institution(null, request.name(), request.type(), cnpj)));
    }

    record InstitutionRequest(@NotBlank String name, @NotNull InstitutionType type, String cnpj) {
    }

    record InstitutionResponse(Long id, String name, InstitutionType type, String cnpj) {

        static InstitutionResponse from(Institution institution) {
            return new InstitutionResponse(institution.id(), institution.name(), institution.type(),
                    institution.cnpj() == null ? null : institution.cnpj().value());
        }
    }
}
