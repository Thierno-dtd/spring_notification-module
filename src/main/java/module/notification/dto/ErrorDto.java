package module.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorDto {

    private int httpCode;
    private String message;
    private String error;
    private String path;
    private LocalDateTime timestamp;
    private List<FieldError> fieldErrors;
    private String traceId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }

    public static ErrorDto of(int httpCode, String message) {
        return ErrorDto.builder()
                .httpCode(httpCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static ErrorDto of(int httpCode, String message, String error) {
        return ErrorDto.builder()
                .httpCode(httpCode)
                .message(message)
                .error(error)
                .timestamp(LocalDateTime.now())
                .build();
    }
}