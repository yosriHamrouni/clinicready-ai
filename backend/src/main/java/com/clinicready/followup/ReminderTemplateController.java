package com.clinicready.followup;

import java.util.List;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinicready.common.Language;
import com.clinicready.followup.dto.ReminderTemplateResponse;
import com.clinicready.followup.dto.TextRequest;

/** Reminder templates of the clinic. Managers only (checked in {@link ReminderTemplates}). */
@RestController
@RequestMapping("/api/v1/reminder-templates")
class ReminderTemplateController {

    /** What the template editor needs: the slots a template may use, and the clinic's templates. */
    record TemplatesResponse(Set<String> slots, List<ReminderTemplateResponse> templates) {
    }

    private final ReminderTemplates templates;

    ReminderTemplateController(ReminderTemplates templates) {
        this.templates = templates;
    }

    @GetMapping
    TemplatesResponse list() {
        return new TemplatesResponse(ReminderTemplates.SLOTS,
                templates.list().stream().map(ReminderTemplateResponse::from).toList());
    }

    @PutMapping("/{requirementCode}/{language}")
    ReminderTemplateResponse save(@PathVariable String requirementCode, @PathVariable Language language,
            @RequestBody TextRequest body) {
        return ReminderTemplateResponse.from(templates.save(requirementCode, language, body.body()));
    }

    @DeleteMapping("/{requirementCode}/{language}")
    ResponseEntity<Void> delete(@PathVariable String requirementCode, @PathVariable Language language) {
        templates.delete(requirementCode, language);
        return ResponseEntity.noContent().build();
    }
}
