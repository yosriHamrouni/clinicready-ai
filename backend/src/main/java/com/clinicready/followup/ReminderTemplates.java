package com.clinicready.followup;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinicready.audit.AuditAction;
import com.clinicready.audit.AuditService;
import com.clinicready.common.InvalidRequestException;
import com.clinicready.common.Language;
import com.clinicready.common.NotFoundException;
import com.clinicready.document.DocumentCategory;
import com.clinicready.readiness.ReadinessEngine;
import com.clinicready.readiness.ReadinessService;
import com.clinicready.security.AuthorizationService;
import com.clinicready.security.CurrentUserService;
import com.clinicready.security.Permission;

/**
 * Reminder templates of the current clinic and how drafts are built from them. A template is fixed text plus
 * {@code {{slot}}} markers from {@link #SLOTS}; code fills the slots from the task, its appointment and the
 * clinic. No model writes into a draft, and no slot carries medical content (no appointment type, no
 * examination names, no document text).
 */
@Service
public class ReminderTemplates {

    /** Requirement code of the clinic's fallback template, used when no template matches the requirement. */
    public static final String DEFAULT_CODE = "DEFAULT";
    public static final Set<String> SLOTS = Set.of("patientName", "requirement", "appointmentDate", "clinicName");
    static final int MAX_TEMPLATE_LENGTH = 1000;
    static final String AUDIT_ENTITY = "REMINDER_TEMPLATE";

    private static final Pattern SLOT = Pattern.compile("\\{\\{\\s*([A-Za-z]*)\\s*}}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ReadinessService.CLINIC_ZONE);

    static final Map<Language, String> BUILT_IN = Map.of(
            Language.FR, "Bonjour {{patientName}}, pour votre rendez-vous du {{appointmentDate}} à {{clinicName}}, "
                    + "merci de nous transmettre : {{requirement}}. Cordialement, l'accueil.",
            Language.AR, "مرحبا {{patientName}}، بخصوص موعدكم يوم {{appointmentDate}} في {{clinicName}}، "
                    + "نرجو تزويدنا بـ: {{requirement}}. مع تحيات الاستقبال.");

    private static final Map<String, Map<Language, String>> LABELS = Map.ofEntries(
            label("ID_DOCUMENT", "votre pièce d'identité", "بطاقة التعريف"),
            label("INSURANCE_CARD", "votre carte d'assurance", "بطاقة التأمين"),
            label("PRESCRIPTION", "votre ordonnance", "الوصفة"),
            label("REFERRAL_LETTER", "votre lettre d'orientation", "رسالة التوجيه"),
            label("CONSENT_FORM", "le formulaire de consentement signé", "استمارة الموافقة الممضاة"),
            label("LAB_RESULT", "vos résultats d'analyses", "نتائج التحاليل"),
            label("IMAGING_REPORT", "votre compte rendu d'imagerie", "تقرير الأشعة"),
            label("OTHER", "le document demandé", "الوثيقة المطلوبة"),
            label("EXPECTED_EXAMINATIONS", "la liste des examens prévus", "قائمة الفحوصات المطلوبة"),
            label("ADVANCE_PAYMENT", "la confirmation de l'avance", "تأكيد الدفعة المسبقة"));

    /** Requirement codes a template may target: every document category, the built-in kinds, and DEFAULT. */
    static final Set<String> CODES = Stream.concat(
            Stream.concat(Arrays.stream(DocumentCategory.values()).map(Enum::name),
                    Stream.of(ReadinessEngine.Kind.EXPECTED_EXAMINATIONS.name(),
                            ReadinessEngine.Kind.ADVANCE_PAYMENT.name())),
            Stream.of(DEFAULT_CODE)).collect(Collectors.toUnmodifiableSet());

    /** A built draft text, and the template it came from (null: built-in text). */
    public record Rendered(String body, UUID templateId) {
    }

    private final ReminderTemplateRepository templates;
    private final CurrentUserService currentUser;
    private final AuthorizationService authorization;
    private final AuditService audit;

    ReminderTemplates(ReminderTemplateRepository templates, CurrentUserService currentUser,
            AuthorizationService authorization, AuditService audit) {
        this.templates = templates;
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ReminderTemplate> list() {
        authorization.require(Permission.TEMPLATE_MANAGE);
        return templates.findAllByClinicIdOrderByRequirementCodeAscLanguageAsc(currentUser.clinicId());
    }

    /** Creates or replaces the template. @throws InvalidRequestException unknown code or slot, bad length */
    @Transactional
    public ReminderTemplate save(String requirementCode, Language language, String body) {
        authorization.require(Permission.TEMPLATE_MANAGE);
        requireCode(requirementCode);
        String text = body == null ? "" : body.strip();
        if (text.isEmpty() || text.length() > MAX_TEMPLATE_LENGTH) {
            throw new InvalidRequestException("A template is 1 to " + MAX_TEMPLATE_LENGTH + " characters");
        }
        requireKnownSlots(text);
        ReminderTemplate template = templates
                .findByClinicIdAndRequirementCodeAndLanguage(currentUser.clinicId(), requirementCode, language)
                .orElseGet(() -> new ReminderTemplate(currentUser.clinicId(), requirementCode, language, text,
                        currentUser.userId()));
        template.update(text, currentUser.userId());
        templates.saveAndFlush(template);
        audit.record(AuditAction.REMINDER_TEMPLATE_SAVED, AUDIT_ENTITY, template.getId(), summary(template));
        return template;
    }

    /** @throws NotFoundException no such template in the clinic */
    @Transactional
    public void delete(String requirementCode, Language language) {
        authorization.require(Permission.TEMPLATE_MANAGE);
        ReminderTemplate template = templates
                .findByClinicIdAndRequirementCodeAndLanguage(currentUser.clinicId(), requirementCode, language)
                .orElseThrow(() -> new NotFoundException("Reminder template"));
        templates.delete(template);
        audit.record(AuditAction.REMINDER_TEMPLATE_DELETED, AUDIT_ENTITY, template.getId(), summary(template));
    }

    /**
     * Builds the draft of a task: the clinic's template for its requirement and language, else the clinic's
     * DEFAULT template, else the built-in text. No permission check: callers have already checked access.
     */
    @Transactional(readOnly = true)
    public Rendered render(FollowUpTask task, Instant appointmentStartsAt, String patientName, String clinicName) {
        Language language = task.getLanguage();
        ReminderTemplate template = templates
                .findByClinicIdAndRequirementCodeAndLanguage(task.getClinicId(), task.getRequirementCode(), language)
                .or(() -> templates.findByClinicIdAndRequirementCodeAndLanguage(task.getClinicId(), DEFAULT_CODE,
                        language))
                .orElse(null);
        String body = template != null ? template.getBody() : BUILT_IN.get(language);
        Map<String, String> values = Map.of(
                "patientName", patientName,
                "requirement", requirementLabel(task.getRequirementCode(), language),
                "appointmentDate", DATE.format(appointmentStartsAt),
                "clinicName", clinicName);
        return new Rendered(fill(body, values), template == null ? null : template.getId());
    }

    /** Replaces every slot; an unknown slot (only possible in data written before validation) is left as is. */
    static String fill(String body, Map<String, String> values) {
        Matcher m = SLOT.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String value = values.get(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }

    static void requireKnownSlots(String body) {
        Matcher m = SLOT.matcher(body);
        while (m.find()) {
            if (!SLOTS.contains(m.group(1))) {
                throw new InvalidRequestException("Unknown slot {{" + m.group(1) + "}}; allowed: "
                        + SLOTS.stream().sorted().map(s -> "{{" + s + "}}").collect(Collectors.joining(", ")));
            }
        }
    }

    static String requirementLabel(String code, Language language) {
        Map<Language, String> labels = LABELS.get(code);
        return labels != null ? labels.get(language) : code;
    }

    private static void requireCode(String code) {
        if (code == null || !CODES.contains(code)) {
            throw new InvalidRequestException("Unknown requirement code " + code);
        }
    }

    private static Map<String, Object> summary(ReminderTemplate t) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requirementCode", t.getRequirementCode());
        summary.put("language", t.getLanguage().name());
        summary.put("length", t.getBody().length());
        return summary;
    }

    private static Map.Entry<String, Map<Language, String>> label(String code, String fr, String ar) {
        return Map.entry(code, Map.of(Language.FR, fr, Language.AR, ar));
    }
}
