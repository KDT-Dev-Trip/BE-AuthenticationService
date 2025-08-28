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
public class TeamCreatedEvent {
    
    @JsonProperty("event_type")
    @Builder.Default
    private String eventType = "auth.team-created";
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("team_id")
    private Long teamId;
    
    @JsonProperty("creator_user_id")
    private Long creatorUserId;
    
    @JsonProperty("creator_auth_user_id")
    private String creatorAuthUserId;
    
    private String teamName;
    private String teamDescription;
    private LocalDateTime createdAt;
    private Integer maxMembers;
    
    @JsonProperty("timestamp")
    private long timestamp;
}