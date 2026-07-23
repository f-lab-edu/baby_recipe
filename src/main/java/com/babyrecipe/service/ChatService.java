package com.babyrecipe.service;

import com.babyrecipe.domain.ChatMessage;
import com.babyrecipe.domain.ChatParticipant;
import com.babyrecipe.domain.ChatRoom;
import com.babyrecipe.domain.User;
import com.babyrecipe.dto.response.ChatMessageResponse;
import com.babyrecipe.dto.response.ChatRoomResponse;
import com.babyrecipe.exception.BabyRecipeException;
import com.babyrecipe.repository.ChatMessageRepository;
import com.babyrecipe.repository.ChatParticipantRepository;
import com.babyrecipe.repository.ChatRoomRepository;
import com.babyrecipe.repository.FollowRepository;
import com.babyrecipe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    @Transactional
    public ChatRoomResponse getOrCreateRoom(Long userId, Long partnerId) {
        if (userId.equals(partnerId)) {
            throw BabyRecipeException.badRequest("자기 자신과 채팅할 수 없습니다.");
        }
        boolean canChat = followRepository.existsByFollowerIdAndFollowingId(userId, partnerId)
            || followRepository.existsByFollowerIdAndFollowingId(partnerId, userId);
        if (!canChat) {
            throw BabyRecipeException.forbidden();
        }

        User partner = findUser(partnerId);
        ChatRoom room = chatRoomRepository.findDirectRoomId(userId, partnerId)
            .map(id -> chatRoomRepository.findById(id).orElseThrow(() -> BabyRecipeException.notFound("채팅방")))
            .orElseGet(() -> createRoom(userId, partnerId));

        ChatMessage lastMessage = chatMessageRepository.findTopByChatRoomIdOrderByCreatedAtDesc(room.getId())
            .orElse(null);
        return ChatRoomResponse.from(room, partner, lastMessage);
    }

    public List<ChatRoomResponse> getRooms(Long userId) {
        return chatParticipantRepository.findByUserId(userId).stream()
            .map(ChatParticipant::getChatRoom)
            .map(room -> {
                User partner = chatParticipantRepository.findByChatRoomId(room.getId()).stream()
                    .map(ChatParticipant::getUser)
                    .filter(u -> !u.getId().equals(userId))
                    .findFirst()
                    .orElseThrow(() -> BabyRecipeException.notFound("상대방"));
                ChatMessage lastMessage = chatMessageRepository.findTopByChatRoomIdOrderByCreatedAtDesc(room.getId())
                    .orElse(null);
                return ChatRoomResponse.from(room, partner, lastMessage);
            })
            .toList();
    }

    public Page<ChatMessageResponse> getMessages(Long roomId, Long userId, Pageable pageable) {
        requireParticipant(roomId, userId);
        return chatMessageRepository.findByChatRoomIdOrderByCreatedAtDesc(roomId, pageable)
            .map(ChatMessageResponse::from);
    }

    @Transactional
    public ChatMessageResponse sendMessage(Long roomId, Long senderId, String content) {
        requireParticipant(roomId, senderId);
        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> BabyRecipeException.notFound("채팅방"));
        User sender = findUser(senderId);

        ChatMessage message = ChatMessage.builder()
            .chatRoom(room).sender(sender).content(content)
            .build();
        chatMessageRepository.save(message);
        return ChatMessageResponse.from(message);
    }

    private ChatRoom createRoom(Long userId, Long partnerId) {
        ChatRoom room = chatRoomRepository.save(ChatRoom.create());
        User user = findUser(userId);
        User partner = findUser(partnerId);
        chatParticipantRepository.save(ChatParticipant.builder().chatRoom(room).user(user).build());
        chatParticipantRepository.save(ChatParticipant.builder().chatRoom(room).user(partner).build());
        return room;
    }

    private void requireParticipant(Long roomId, Long userId) {
        if (!chatParticipantRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            throw BabyRecipeException.forbidden();
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> BabyRecipeException.notFound("사용자"));
    }
}
