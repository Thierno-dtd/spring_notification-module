package module.notification.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import module.notification.dto.ErrorDto;
import module.notification.exceptions.NotificationException;
import module.notification.exceptions.TemplateNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice(basePackages = "module.notification.controllers")
@Slf4j
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationException.class)
    public ResponseEntity<ErrorDto> handleNotificationException(NotificationException ex) {
        log.error("Erreur de notification: {}", ex.getMessage(), ex);

        ErrorDto error = ErrorDto.builder()
                .httpCode(HttpStatus.BAD_REQUEST.value())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(TemplateNotFoundException.class)
    public ResponseEntity<ErrorDto> handleTemplateNotFoundException(TemplateNotFoundException ex) {
        log.error("Template non trouvé: {}", ex.getMessage());

        ErrorDto error = ErrorDto.builder()
                .httpCode(HttpStatus.NOT_FOUND.value())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorDto> handleValidationException(Exception ex) {
        String message;

        if (ex instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException validEx = (MethodArgumentNotValidException) ex;
            message = validEx.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
        } else {
            BindException bindEx = (BindException) ex;
            message = bindEx.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .collect(Collectors.joining(", "));
        }

        ErrorDto error = ErrorDto.builder()
                .httpCode(HttpStatus.BAD_REQUEST.value())
                .message("Erreur de validation: " + message)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolationException(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));

        ErrorDto error = ErrorDto.builder()
                .httpCode(HttpStatus.BAD_REQUEST.value())
                .message("Erreur de validation: " + message)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGenericException(Exception ex) {
        log.error("Erreur inattendue: {}", ex.getMessage(), ex);

        ErrorDto error = ErrorDto.builder()
                .httpCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("Erreur interne du serveur")
                .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

