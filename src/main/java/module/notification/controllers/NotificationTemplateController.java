package module.notification.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import module.notification.dto.NotificationTemplateDto;
import module.notification.services.servicesImpl.NotificationTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification-templates")
@RequiredArgsConstructor

public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    @PostMapping
    @Operation(summary = "Créer un template de notification", description = "Crée un nouveau template de notification")
    public ResponseEntity<NotificationTemplateDto> createTemplate(
            @Valid @RequestBody NotificationTemplateDto templateDto) {

        NotificationTemplateDto created = templateService.createTemplate(templateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "Récupérer un template par ID", description = "Récupère un template de notification par son identifiant")
    public ResponseEntity<NotificationTemplateDto> getTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId) {

        NotificationTemplateDto template = templateService.getTemplate(templateId);
        return ResponseEntity.ok(template);
    }

    @GetMapping
    @Operation(summary = "Récupérer tous les templates actifs", description = "Récupère la liste de tous les templates actifs")
    public ResponseEntity<List<NotificationTemplateDto>> getAllTemplates() {
        List<NotificationTemplateDto> templates = templateService.getAllActiveTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/by-type/{type}")
    @Operation(summary = "Récupérer les templates par type", description = "Récupère les templates filtrés par type de notification")
    public ResponseEntity<List<NotificationTemplateDto>> getTemplatesByType(
            @PathVariable @Parameter(description = "Type de notification") String type) {

        List<NotificationTemplateDto> templates = templateService.getTemplatesByType(type);
        return ResponseEntity.ok(templates);
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "Mettre à jour un template", description = "Met à jour un template de notification existant")
    public ResponseEntity<NotificationTemplateDto> updateTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId,
            @Valid @RequestBody NotificationTemplateDto templateDto) {

        NotificationTemplateDto updated = templateService.updateTemplate(templateId, templateDto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "Supprimer un template", description = "Désactive un template de notification")
    public ResponseEntity<Void> deleteTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId) {

        templateService.deleteTemplate(templateId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{templateId}/test")
    @Operation(summary = "Tester un template", description = "Teste un template avec des données d'exemple")
    public ResponseEntity<String> testTemplate(
            @PathVariable @Parameter(description = "ID du template") String templateId,
            @RequestBody(required = false) java.util.Map<String, String> testParameters) {

        String result = templateService.testTemplate(templateId, testParameters);
        return ResponseEntity.ok(result);
    }
}