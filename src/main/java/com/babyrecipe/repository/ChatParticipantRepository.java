package com.babyrecipe.repository;

import com.babyrecipe.domain.ChatParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    boolean existsByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    @Query("SELECT cp FROM ChatParticipant cp JOIN FETCH cp.user WHERE cp.chatRoom.id = :chatRoomId")
    List<ChatParticipant> findByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    @Query("SELECT cp FROM ChatParticipant cp JOIN FETCH cp.chatRoom WHERE cp.user.id = :userId")
    List<ChatParticipant> findByUserId(@Param("userId") Long userId);
}
