package ac.su.kdt.beauthenticationservice.repository;

import ac.su.kdt.beauthenticationservice.model.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {
    
    /**
     * 초대 코드로 팀 찾기
     */
    Optional<Team> findByInviteCode(String inviteCode);
    
    /**
     * 팀 이름으로 찾기
     */
    Optional<Team> findByName(String name);
    
    /**
     * 소유자 ID로 팀 목록 찾기
     */
    List<Team> findByOwnerIdAndIsActive(String ownerId, Boolean isActive);
    
    /**
     * 공개 팀 목록 찾기
     */
    List<Team> findByIsPublicAndIsActive(Boolean isPublic, Boolean isActive);
    
    /**
     * 활성 팀 개수
     */
    long countByIsActive(Boolean isActive);
    
    /**
     * 유효한 초대 코드가 있는 팀 찾기
     */
    @Query("SELECT t FROM Team t WHERE t.inviteCode IS NOT NULL AND t.inviteCodeExpiresAt > CURRENT_TIMESTAMP AND t.isActive = true")
    List<Team> findTeamsWithValidInviteCodes();
    
    /**
     * 팀 이름으로 검색 (부분 일치)
     */
    @Query("SELECT t FROM Team t WHERE t.name LIKE %:name% AND t.isActive = true")
    List<Team> searchByNameContaining(@Param("name") String name);
}