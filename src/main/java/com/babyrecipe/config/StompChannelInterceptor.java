package com.babyrecipe.config;

import com.babyrecipe.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_ROOM_TOPIC = Pattern.compile("^/topic/chat\\.(\\d+)$");

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }

        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || !jwtProvider.validateToken(token)) {
            throw new MessagingException("유효하지 않은 인증 정보입니다.");
        }

        Long userId = jwtProvider.getUserId(token);
        UserDetails userDetails = userDetailsService.loadUserByUsername(String.valueOf(userId));
        var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        accessor.setUser(auth);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = CHAT_ROOM_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            return;
        }

        Long roomId = Long.parseLong(matcher.group(1));
        UserDetails userDetails = (UserDetails) ((UsernamePasswordAuthenticationToken) accessor.getUser()).getPrincipal();
        Long userId = Long.parseLong(userDetails.getUsername());
        if (!chatParticipantRepository.existsByChatRoomIdAndUserId(roomId, userId)) {
            throw new MessagingException("채팅방 참여자만 구독할 수 있습니다.");
        }
    }

    private String extractToken(String header) {
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
