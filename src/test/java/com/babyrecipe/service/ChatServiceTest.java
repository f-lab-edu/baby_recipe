package com.babyrecipe.service;

import com.babyrecipe.domain.ChatRoom;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.response.ChatRoomResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.ChatMessageRepository;
import com.babyrecipe.repository.ChatParticipantRepository;
import com.babyrecipe.repository.ChatRoomRepository;
import com.babyrecipe.repository.FollowRepository;
import com.babyrecipe.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @InjectMocks ChatService chatService;
    @Mock ChatRoomRepository chatRoomRepository;
    @Mock ChatParticipantRepository chatParticipantRepository;
    @Mock ChatMessageRepository chatMessageRepository;
    @Mock UserRepository userRepository;
    @Mock FollowRepository followRepository;

    @Test
    @DisplayName("팔로우 관계인 두 유저 사이에 기존 채팅방이 없으면 새로 생성한다")
    void getOrCreateRoom_존재하지않으면_새로생성() {
        User user = User.builder().email("a@test.com").password("pw").nickname("A").build();
        setField(user, "id", 1L);
        User partner = User.builder().email("b@test.com").password("pw").nickname("B").build();
        setField(partner, "id", 2L);

        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(userRepository.findById(2L)).willReturn(Optional.of(partner));
        given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(true);
        given(chatRoomRepository.findDirectRoomId(1L, 2L)).willReturn(Optional.empty());
        given(chatRoomRepository.save(any(ChatRoom.class))).willAnswer(inv -> {
            ChatRoom room = inv.getArgument(0);
            setField(room, "id", 10L);
            return room;
        });
        given(chatMessageRepository.findTopByChatRoomIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());

        ChatRoomResponse response = chatService.getOrCreateRoom(1L, 2L);

        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getPartner().getNickname()).isEqualTo("B");
    }

    @Test
    @DisplayName("이미 채팅방이 있으면 기존 방을 반환한다")
    void getOrCreateRoom_이미존재하면_기존방반환() {
        User partner = User.builder().email("b@test.com").password("pw").nickname("B").build();
        setField(partner, "id", 2L);
        ChatRoom existingRoom = ChatRoom.create();
        setField(existingRoom, "id", 10L);

        given(userRepository.findById(2L)).willReturn(Optional.of(partner));
        given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(true);
        given(chatRoomRepository.findDirectRoomId(1L, 2L)).willReturn(Optional.of(10L));
        given(chatRoomRepository.findById(10L)).willReturn(Optional.of(existingRoom));
        given(chatMessageRepository.findTopByChatRoomIdOrderByCreatedAtDesc(10L)).willReturn(Optional.empty());

        ChatRoomResponse response = chatService.getOrCreateRoom(1L, 2L);

        assertThat(response.getId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("자기 자신과 채팅을 시도하면 예외가 발생한다")
    void getOrCreateRoom_자기자신에게시도하면_예외() {
        assertThatThrownBy(() -> chatService.getOrCreateRoom(1L, 1L))
            .isInstanceOf(BabyRecipeException.class)
            .hasMessageContaining("자기 자신");
    }

    @Test
    @DisplayName("팔로우 관계가 없으면 채팅방 생성 시 예외가 발생한다")
    void getOrCreateRoom_팔로우관계없으면_예외() {
        given(followRepository.existsByFollowerIdAndFollowingId(1L, 2L)).willReturn(false);
        given(followRepository.existsByFollowerIdAndFollowingId(2L, 1L)).willReturn(false);

        assertThatThrownBy(() -> chatService.getOrCreateRoom(1L, 2L))
            .isInstanceOf(BabyRecipeException.class)
            .hasMessageContaining("권한");
    }

    @Test
    @DisplayName("참여자가 아니면 메시지 전송 시 예외가 발생한다")
    void sendMessage_참여자가아니면_예외() {
        given(chatParticipantRepository.existsByChatRoomIdAndUserId(10L, 3L)).willReturn(false);

        assertThatThrownBy(() -> chatService.sendMessage(10L, 3L, "안녕하세요"))
            .isInstanceOf(BabyRecipeException.class)
            .hasMessageContaining("권한");
    }

    @Test
    @DisplayName("참여자가 아니면 메시지 조회 시 예외가 발생한다")
    void getMessages_참여자가아니면_예외() {
        given(chatParticipantRepository.existsByChatRoomIdAndUserId(10L, 3L)).willReturn(false);

        assertThatThrownBy(() -> chatService.getMessages(10L, 3L, PageRequest.of(0, 30)))
            .isInstanceOf(BabyRecipeException.class)
            .hasMessageContaining("권한");
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = findField(target.getClass(), fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.lang.reflect.Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new RuntimeException("Field not found: " + name);
    }
}
