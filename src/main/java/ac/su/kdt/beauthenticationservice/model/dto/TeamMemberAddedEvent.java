package ac.su.kdt.beauthenticationservice.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberAddedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.team-member-added";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("team_id")
    private Long teamId;
    
    @JsonProperty("user_id")
    private Long userId;
    
    @JsonProperty("auth_user_id")
    private String authUserId;
    
    @JsonProperty("added_by_user_id")
    private Long addedByUserId;
    
    private String teamName;
    private String userEmail;
    private String userName;
    private String memberRole; // "LEADER", "MEMBER", "VIEWER"
    private LocalDateTime joinedAt;
    
    @JsonProperty("timestamp")
    private long timestamp;
}