package ac.su.kdt.beauthenticationservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 이메일 로그인 요청 DTO
 * 이메일 로그인 요청 DTO는 사용자가 이메일과 비밀번호를 입력하여 로그인을 시도할 때 사용됩니다.
 */

@Data
public class LoginRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    private String password;
    
    private boolean rememberMe = false;
}