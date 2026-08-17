package com.audittrove.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Device Registration")
public class DeviceRegistrationController {

    private final DeviceTokenService tokenService;
    private final PushTokenStore pushTokenStore;

    public DeviceRegistrationController(DeviceTokenService tokenService,
                                        PushTokenStore pushTokenStore) {
        this.tokenService = tokenService;
        this.pushTokenStore = pushTokenStore;
    }

    public record DeviceRegistrationRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "deviceId geçerli bir UUID olmalıdır")
            String deviceId
    ) {}

    @PostMapping("/devices")
    @Operation(summary = "Cihaz kaydı yapar ve erişim token'ı döner")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody DeviceRegistrationRequest request) {
        if (!tokenService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Cihaz kaydı şu anda yapılandırılmamış."));
        }
        return ResponseEntity.ok(Map.of("token", tokenService.issue(request.deviceId())));
    }

    // Cihazin Expo push token'ini kaydeder (auth'lu; deviceId token'dan cozulur).
    @PostMapping("/devices/push-token")
    @Operation(summary = "Cihazın push bildirimi token'ını kaydeder")
    public ResponseEntity<Void> registerPushToken(@RequestBody Map<String, String> body,
                                                  HttpServletRequest request) {
        String deviceId = (String) request.getAttribute(MobileAuthFilter.DEVICE_ID_ATTR);
        if (deviceId != null) {
            pushTokenStore.put(deviceId, body.get("pushToken"));
        }
        return ResponseEntity.ok().build();
    }
}