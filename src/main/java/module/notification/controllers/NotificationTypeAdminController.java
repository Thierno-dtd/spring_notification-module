package module.notification.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import module.notification.dto.CreateNotificationTypeDto;
import module.notification.entities.NotificationType;
import module.notification.services.servicesImpl.NotificationTypeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/notification-types")
@RequiredArgsConstructor
@Tag(name = "Administration - Types de notification")
@SecurityRequirement(name = "admin-auth")
public class NotificationTypeAdminController {

    private final NotificationTypeService notificationTypeService;

    @GetMapping
    @Operation(summary = "Lister tous les types de notification")
    public ResponseEntity<List<NotificationType>> getAll() {
        return ResponseEntity.ok(notificationTypeService.getAll());
    }

    @PostMapping
    @Operation(summary = "Créer un nouveau type de notification")
    public ResponseEntity<NotificationType> create(@Valid @RequestBody CreateNotificationTypeDto request) {
        return ResponseEntity.ok(notificationTypeService.create(
                request.getCode(), request.getLabel(), request.getDescription()));
    }

    @PatchMapping("/{code}/activate")
    public ResponseEntity<NotificationType> activate(@PathVariable String code) {
        return ResponseEntity.ok(notificationTypeService.setActive(code, true));
    }

    @PatchMapping("/{code}/deactivate")
    public ResponseEntity<NotificationType> deactivate(@PathVariable String code) {
        return ResponseEntity.ok(notificationTypeService.setActive(code, false));
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        notificationTypeService.delete(code);
        return ResponseEntity.noContent().build();
    }
}