package ac.su.kdt.beauthenticationservice.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
// 사용자 동기화 이벤트
// 사용자 동기화 이벤트는 사용자 정보를 동기화할 때 발생하는 이벤트입니다.
// 이벤트는 동기화 유형, 사용자 정보 목록, 타임스탬프 등의 정보를 포함합니다.

public class UserSyncEvent {
    
    private String eventType; // "FULL_SYNC", "UPDATE", "DELETE"
    private List<UserSyncData> users;
    private long timestamp;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserSyncData {
        @JsonProperty("authUserId")
        private String userId;
        private String email;
        private String name;
        private String planType;
        private LocalDateTime signupTimestamp;
        private String source;
        private String socialProvider;
        private String status; // "ACTIVE", "INACTIVE", "DELETED"
    }
    
    public static UserSyncEvent createFullSync(List<UserSyncData> users) {
        return UserSyncEvent.builder()
                .eventType("FULL_SYNC")
                .users(users)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static UserSyncEvent createUpdate(UserSyncData user) {
        return UserSyncEvent.builder()
                .eventType("UPDATE")
                .users(List.of(user))
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static UserSyncEvent createDelete(String userId) {
        UserSyncData deleteData = UserSyncData.builder()
                .userId(userId)
                .status("DELETED")
                .build();
        
        return UserSyncEvent.builder()
                .eventType("DELETE")
                .users(List.of(deleteData))
                .timestamp(System.currentTimeMillis())
                .build();
    }
}