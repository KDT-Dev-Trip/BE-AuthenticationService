package ac.su.kdt.beauthenticationservice.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 이메일 회원가입 요청 DTO
 */
@Data
public class SignupRequest {
    
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    private String email;
    
    @NotBlank(message = "비밀번호는 필수 입력값입니다.")
    @Size(min = 8, max = 128, message = "비밀번호는 8자 이상 128자 이하여야 합니다.")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
        message = "비밀번호는 대문자, 소문자, 숫자, 특수문자를 각각 최소 1개씩 포함해야 합니다."
    )
    private String password;
    
    @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
    private String passwordConfirm;
    
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다.")
    private String name;
    
    @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
    private String nickname;
    
    private boolean agreeToTerms = false;
    
    private boolean agreeToPrivacyPolicy = false;
    
    private boolean agreeToMarketing = false;
    
    /**
     * 비밀번호와 비밀번호 확인이 일치하는지 검증
     */
    public boolean isPasswordMatching() {
        return password != null && password.equals(passwordConfirm);
    }
    
    /**
     * 필수 약관 동의 여부 확인
     */
    public boolean hasRequiredConsents() {
        return agreeToTerms && agreeToPrivacyPolicy;
    }
}