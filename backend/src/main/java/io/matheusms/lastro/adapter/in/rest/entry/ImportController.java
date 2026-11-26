package io.matheusms.lastro.adapter.in.rest.entry;

import io.matheusms.lastro.adapter.in.rest.CurrentUser;
import io.matheusms.lastro.application.ImportService;
import io.matheusms.lastro.domain.model.EntrySource;
import io.matheusms.lastro.domain.model.ImportBatch;
import io.matheusms.lastro.domain.model.ImportStatus;
import io.matheusms.lastro.domain.model.statement.ImportIssue;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload of files exported by B3 (movements, .xlsx) and Binance (transaction history, .csv or .zip). */
@RestController
@RequestMapping("/api/imports")
class ImportController {

    private final ImportService imports;

    ImportController(ImportService imports) {
        this.imports = imports;
    }

    @GetMapping
    List<BatchResponse> list(@CurrentUser Long userId) {
        return imports.list(userId).stream().map(BatchResponse::from).toList();
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ImportResponse upload(@CurrentUser Long userId, @RequestParam EntrySource source,
            @RequestParam Long institutionId, @RequestParam MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("The file is empty");
        }
        var fileName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        var result = imports.importFile(userId, source, institutionId, fileName, file.getBytes());
        return new ImportResponse(BatchResponse.from(result.batch()), result.trades(), result.distributions(),
                result.corporateActions(), result.duplicates(),
                result.issues().stream().map(IssueResponse::from).toList());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@CurrentUser Long userId, @PathVariable Long id) {
        imports.delete(userId, id);
    }

    record BatchResponse(
            Long id, Long institutionId, EntrySource source, String fileName, Instant importedAt,
            ImportStatus status, int processedRows, int skippedRows) {

        static BatchResponse from(ImportBatch batch) {
            return new BatchResponse(batch.id(), batch.institutionId(), batch.source(), batch.fileName(),
                    batch.importedAt(), batch.status(), batch.processedRows(), batch.skippedRows());
        }
    }

    record IssueResponse(int line, ImportIssue.Code code, boolean needsAttention, String description, String detail) {

        static IssueResponse from(ImportIssue issue) {
            return new IssueResponse(issue.line(), issue.code(), issue.code().needsAttention(), issue.description(),
                    issue.detail());
        }
    }

    record ImportResponse(
            BatchResponse batch, int trades, int distributions, int corporateActions, int duplicates,
            List<IssueResponse> issues) {
    }
}
