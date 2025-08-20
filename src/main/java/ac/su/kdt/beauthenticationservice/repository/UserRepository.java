package ac.su.kdt.beauthenticationservice.repository;

import ac.su.kdt.beauthenticationservice.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findBySocialProviderAndSocialUserId(String socialProvider, String socialUserId);
    
    boolean existsByEmail(String email);
    
    boolean existsBySocialProviderAndSocialUserId(String socialProvider, String socialUserId);
    
    @Query("SELECT COUNT(u) FROM User u WHERE u.isActive = true")
    long countActiveUsers();
    
    @Query("SELECT u FROM User u WHERE u.isActive = true AND u.email = :email")
    Optional<User> findActiveByEmail(String email);
    
    @Query("SELECT u FROM User u WHERE u.failedLoginAttempts >= :maxAttempts")
    java.util.List<User> findUsersWithHighFailedLoginAttempts(int maxAttempts);
    
    @Query("SELECT u FROM User u WHERE u.lockedUntil IS NOT NULL AND u.lockedUntil > CURRENT_TIMESTAMP")
    java.util.List<User> findLockedUsers();
}