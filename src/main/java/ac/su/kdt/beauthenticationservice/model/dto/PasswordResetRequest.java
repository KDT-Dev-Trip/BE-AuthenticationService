package ac.su.kdt.beauthenticationservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 비밀번호 재설정 요청 DTO
 */
@Data
public class PasswordResetRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
}