package ac.su.kdt.beauthenticationservice.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "teams")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Team {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String name;
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "invite_code", unique = true)
    private String inviteCode;
    
    @Column(name = "invite_code_expires_at")
    private LocalDateTime inviteCodeExpiresAt;
    
    @Column(name = "max_members")
    private Integer maxMembers = 50;
    
    @Column(name = "is_public")
    private Boolean isPublic = false;
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;
    
    @OneToMany(mappedBy = "team", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<TeamMember> members;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    /**
     * 현재 팀 멤버 수
     */
    public int getCurrentMemberCount() {
        return members != null ? members.size() : 0;
    }
    
    /**
     * 초대 코드 유효성 확인
     */
    public boolean isInviteCodeValid() {
        return inviteCode != null && 
               inviteCodeExpiresAt != null && 
               inviteCodeExpiresAt.isAfter(LocalDateTime.now());
    }
    
    /**
     * 팀 정원 확인
     */
    public boolean canAcceptNewMembers() {
        return getCurrentMemberCount() < maxMembers;
    }
}