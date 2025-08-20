package ac.su.kdt.beauthenticationservice.repository;

import ac.su.kdt.beauthenticationservice.model.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    
    /**
     * 팀과 사용자로 멤버십 찾기
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.user.id = :userId")
    Optional<TeamMember> findByTeamIdAndUserId(@Param("teamId") Long teamId, @Param("userId") String userId);
    
    /**
     * 사용자의 활성 팀 멤버십 목록
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.user.id = :userId AND tm.isActive = :isActive")
    List<TeamMember> findByUserIdAndIsActive(@Param("userId") String userId, @Param("isActive") Boolean isActive);
    
    /**
     * 팀의 활성 멤버 목록
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.isActive = :isActive")
    List<TeamMember> findByTeamIdAndIsActive(@Param("teamId") Long teamId, @Param("isActive") Boolean isActive);
    
    /**
     * 특정 역할을 가진 팀 멤버 찾기
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.role = :role AND tm.isActive = :isActive")
    List<TeamMember> findByTeamIdAndRoleAndIsActive(@Param("teamId") Long teamId, @Param("role") TeamMember.TeamRole role, @Param("isActive") Boolean isActive);
    
    /**
     * 사용자가 소유한 팀 멤버십 찾기
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.user.id = :userId AND tm.role = :role AND tm.isActive = :isActive")
    List<TeamMember> findByUserIdAndRoleAndIsActive(@Param("userId") String userId, @Param("role") TeamMember.TeamRole role, @Param("isActive") Boolean isActive);
    
    /**
     * 팀의 활성 멤버 수 계산
     */
    @Query("SELECT COUNT(tm) FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.isActive = :isActive")
    long countByTeamIdAndIsActive(@Param("teamId") Long teamId, @Param("isActive") Boolean isActive);
    
    /**
     * 특정 초대 코드로 가입한 멤버들 찾기
     */
    List<TeamMember> findByJoinedViaInviteCode(String inviteCode);
    
    /**
     * 사용자가 팀의 소유자 또는 관리자인지 확인
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.user.id = :userId AND tm.role IN ('OWNER', 'ADMIN') AND tm.isActive = true")
    Optional<TeamMember> findTeamAdminMembership(@Param("teamId") Long teamId, @Param("userId") String userId);
    
    /**
     * 팀에서 가장 최근에 가입한 멤버들
     */
    @Query("SELECT tm FROM TeamMember tm WHERE tm.team.id = :teamId AND tm.isActive = :isActive ORDER BY tm.joinedAt DESC")
    List<TeamMember> findByTeamIdAndIsActiveOrderByJoinedAtDesc(@Param("teamId") Long teamId, @Param("isActive") Boolean isActive);
}