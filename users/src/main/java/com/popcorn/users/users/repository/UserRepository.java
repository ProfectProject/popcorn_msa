package com.popcorn.users.users.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import com.popcorn.users.users.entity.User;
import com.popcorn.users.users.entity.enums.UserRole;
import java.util.Optional;
import java.util.List;

public interface UserRepository extends JpaRepository<User, Long>{
    Optional<User> findByEmail(String email); //이메일로 사용자 조회
    Optional<User> findByEmailAndIsActiveTrue(String email); // isActive = true인 사용자만 조회
    Optional<User> findByPhone(String phone); //전화번호로 사용자 조회

    User findByemail(String email);

    List<User> findAllByRoleAndIsActiveTrue(UserRole role);
    List<User> findAllByUserIdInAndIsActiveTrue(List<Long> userIds);

    boolean existsByUserIdAndIsActiveTrue(Long userId);
}
