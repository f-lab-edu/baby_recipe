package com.babyrecipe.repository;

import com.babyrecipe.domain.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT cp1.chatRoom.id FROM ChatParticipant cp1 JOIN ChatParticipant cp2 " +
           "ON cp1.chatRoom.id = cp2.chatRoom.id " +
           "WHERE cp1.user.id = :userId AND cp2.user.id = :partnerId " +
           "AND (SELECT COUNT(cp3) FROM ChatParticipant cp3 WHERE cp3.chatRoom = cp1.chatRoom) = 2")
    Optional<Long> findDirectRoomId(@Param("userId") Long userId, @Param("partnerId") Long partnerId);
}
