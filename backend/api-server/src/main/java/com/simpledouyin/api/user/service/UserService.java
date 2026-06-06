package com.simpledouyin.api.user.service;

import com.simpledouyin.api.common.BusinessException;
import com.simpledouyin.api.common.ErrorCode;
import com.simpledouyin.api.common.RequestContext;
import com.simpledouyin.api.user.dto.MeResponse;
import com.simpledouyin.api.user.dto.UserProfileResponse;
import com.simpledouyin.api.user.model.UserProfile;
import com.simpledouyin.api.user.repository.UserProfileRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserProfileRepository userProfileRepository;

    public UserService(UserProfileRepository userProfileRepository) {
        this.userProfileRepository = userProfileRepository;
    }

    @Transactional(readOnly = true)
    public MeResponse currentUser(HttpServletRequest request) {
        Long currentUserId = RequestContext.currentUserId(request);
        if (currentUserId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        UserProfile profile = userProfileRepository.findProfileById(currentUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return new MeResponse(new UserProfileResponse(
                profile.id(),
                profile.username(),
                profile.nickname(),
                profile.avatarUrl(),
                profile.videoCount(),
                profile.likedCount()
        ));
    }
}
