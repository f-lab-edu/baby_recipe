package com.babyrecipe.dto.response;

import com.babyrecipe.domain.ChatMessage;
import com.babyrecipe.domain.ChatRoom;
import com.babyrecipe.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatRoomResponse {
    private Long id;
    private PartnerInfo partner;
    private String lastMessage;
    private LocalDateTime lastMessageAt;

    @Getter
    @Builder
    public static class PartnerInfo {
        private Long id;
        private String nickname;
        private String profileImage;
    }

    public static ChatRoomResponse from(ChatRoom room, User partner, ChatMessage lastMessage) {
        return ChatRoomResponse.builder()
            .id(room.getId())
            .partner(PartnerInfo.builder()
                .id(partner.getId())
                .nickname(partner.getNickname())
                .profileImage(partner.getProfileImage())
                .build())
            .lastMessage(lastMessage != null ? lastMessage.getContent() : null)
            .lastMessageAt(lastMessage != null ? lastMessage.getCreatedAt() : null)
            .build();
    }
}
