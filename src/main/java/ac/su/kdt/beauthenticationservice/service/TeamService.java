package ac.su.kdt.beauthenticationservice.service;

import ac.su.kdt.beauthenticationservice.model.entity.Team;
import ac.su.kdt.beauthenticationservice.model.entity.TeamMember;
import ac.su.kdt.beauthenticationservice.model.entity.User;
import ac.su.kdt.beauthenticationservice.repository.TeamRepository;
import ac.su.kdt.beauthenticationservice.repository.TeamMemberRepository;
import ac.su.kdt.beauthenticationservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 팀 가입/초대 코드 관리 서비스
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class TeamService {
    
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * 팀 생성
     */
    public Team createTeam(String ownerId, String teamName, String description, Integer maxMembers) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Owner not found"));
        
        Team team = Team.builder()
                .name(teamName)
                .description(description)
                .owner(owner)
                .maxMembers(maxMembers != null ? maxMembers : 50)
                .isPublic(false)
                .isActive(true)
                .build();
        
        team = teamRepository.save(team);
        
        // 소유자를 팀 멤버로 추가
        TeamMember ownerMembership = TeamMember.builder()
                .team(team)
                .user(owner)
                .role(TeamMember.TeamRole.OWNER)
                .isActive(true)
                .build();
        
        teamMemberRepository.save(ownerMembership);
        
        log.info("Created team '{}' for owner: {}", teamName, ownerId);
        return team;
    }
    
    /**
     * 초대 코드 생성
     */
    public String generateInviteCode(Long teamId, String requesterId, int validityHours) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        
        // 권한 확인 (소유자 또는 관리자만)
        TeamMember requester = teamMemberRepository.findByTeamIdAndUserId(teamId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this team"));
        
        if (requester.getRole() != TeamMember.TeamRole.OWNER && 
            requester.getRole() != TeamMember.TeamRole.ADMIN) {
            throw new IllegalArgumentException("Insufficient permissions to generate invite code");
        }
        
        // 안전한 초대 코드 생성 (8자리 대문자 + 숫자)
        String inviteCode = generateSecureCode(8);
        
        team.setInviteCode(inviteCode);
        team.setInviteCodeExpiresAt(LocalDateTime.now().plusHours(validityHours));
        
        teamRepository.save(team);
        
        log.info("Generated invite code for team '{}': {}", team.getName(), inviteCode);
        return inviteCode;
    }
    
    /**
     * 초대 코드로 팀 가입
     */
    public TeamMember joinTeamByInviteCode(String userId, String inviteCode) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        
        Team team = teamRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        
        // 초대 코드 유효성 확인
        if (!team.isInviteCodeValid()) {
            throw new IllegalArgumentException("Invite code is expired");
        }
        
        // 팀 정원 확인
        if (!team.canAcceptNewMembers()) {
            throw new IllegalArgumentException("Team is full");
        }
        
        // 이미 멤버인지 확인
        Optional<TeamMember> existingMembership = teamMemberRepository.findByTeamIdAndUserId(team.getId(), userId);
        if (existingMembership.isPresent()) {
            if (existingMembership.get().getIsActive()) {
                throw new IllegalArgumentException("User is already a member of this team");
            } else {
                // 비활성 멤버십을 다시 활성화
                TeamMember membership = existingMembership.get();
                membership.setIsActive(true);
                membership.setJoinedViaInviteCode(inviteCode);
                
                log.info("Reactivated team membership for user: {} in team: {}", userId, team.getName());
                return teamMemberRepository.save(membership);
            }
        }
        
        // 새로운 멤버십 생성
        TeamMember membership = TeamMember.builder()
                .team(team)
                .user(user)
                .role(TeamMember.TeamRole.MEMBER)
                .joinedViaInviteCode(inviteCode)
                .isActive(true)
                .build();
        
        membership = teamMemberRepository.save(membership);
        
        log.info("User {} joined team '{}' via invite code: {}", userId, team.getName(), inviteCode);
        return membership;
    }
    
    /**
     * 팀 탈퇴
     */
    public void leaveTeam(String userId, Long teamId) {
        TeamMember membership = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this team"));
        
        if (membership.getRole() == TeamMember.TeamRole.OWNER) {
            throw new IllegalArgumentException("Team owner cannot leave the team. Transfer ownership first.");
        }
        
        membership.setIsActive(false);
        teamMemberRepository.save(membership);
        
        log.info("User {} left team: {}", userId, teamId);
    }
    
    /**
     * 사용자의 팀 목록 조회
     */
    public List<TeamMember> getUserTeams(String userId) {
        return teamMemberRepository.findByUserIdAndIsActive(userId, true);
    }
    
    /**
     * 팀 멤버 목록 조회
     */
    public List<TeamMember> getTeamMembers(Long teamId) {
        return teamMemberRepository.findByTeamIdAndIsActive(teamId, true);
    }
    
    /**
     * 초대 코드로 팀 정보 미리보기
     */
    public Team getTeamByInviteCode(String inviteCode) {
        Team team = teamRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid invite code"));
        
        if (!team.isInviteCodeValid()) {
            throw new IllegalArgumentException("Invite code is expired");
        }
        
        return team;
    }
    
    /**
     * 초대 코드 무효화
     */
    public void revokeInviteCode(Long teamId, String requesterId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        
        // 권한 확인
        TeamMember requester = teamMemberRepository.findByTeamIdAndUserId(teamId, requesterId)
                .orElseThrow(() -> new IllegalArgumentException("User is not a member of this team"));
        
        if (requester.getRole() != TeamMember.TeamRole.OWNER && 
            requester.getRole() != TeamMember.TeamRole.ADMIN) {
            throw new IllegalArgumentException("Insufficient permissions to revoke invite code");
        }
        
        team.setInviteCode(null);
        team.setInviteCodeExpiresAt(null);
        
        teamRepository.save(team);
        
        log.info("Revoked invite code for team: {}", team.getName());
    }
    
    /**
     * 안전한 초대 코드 생성 (8자리 대문자 + 숫자)
     */
    private String generateSecureCode(int length) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < length; i++) {
            int index = secureRandom.nextInt(characters.length());
            code.append(characters.charAt(index));
        }
        
        return code.toString();
    }
    
    /**
     * 팀 통계 조회
     */
    public TeamStats getTeamStats(Long teamId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new IllegalArgumentException("Team not found"));
        
        List<TeamMember> activeMembers = teamMemberRepository.findByTeamIdAndIsActive(teamId, true);
        
        return TeamStats.builder()
                .teamId(teamId)
                .teamName(team.getName())
                .totalMembers(activeMembers.size())
                .maxMembers(team.getMaxMembers())
                .hasActiveInviteCode(team.isInviteCodeValid())
                .inviteCodeExpiresAt(team.getInviteCodeExpiresAt())
                .isPublic(team.getIsPublic())
                .createdAt(team.getCreatedAt())
                .build();
    }
    
    /**
     * 팀 통계 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class TeamStats {
        private Long teamId;
        private String teamName;
        private Integer totalMembers;
        private Integer maxMembers;
        private Boolean hasActiveInviteCode;
        private LocalDateTime inviteCodeExpiresAt;
        private Boolean isPublic;
        private LocalDateTime createdAt;
    }
}