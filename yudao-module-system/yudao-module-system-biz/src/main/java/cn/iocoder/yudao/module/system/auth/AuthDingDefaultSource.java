package cn.iocoder.yudao.module.system.auth;

import com.xingyuv.jushauth.config.AuthSource;
import com.xingyuv.jushauth.request.AuthDefaultRequest;
import com.xingyuv.jushauth.request.AuthDingTalkRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AuthDingDefaultSource implements AuthSource {
    
    DINGTALK_LEMON {
        @Override
        public String authorize() {
            return "https://login.dingtalk.com/oauth2/auth";
        }

        @Override
        public String accessToken() {
            return "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
        }

        @Override
        public String userInfo() {
            return "https://api.dingtalk.com/v1.0/contact/users/me";
        }

        @Override
        public Class<? extends AuthDefaultRequest> getTargetClass() {
            return AuthDingTalkRequest.class;
        }
    };

    @Override
    public String getName() {
        return this.name();
    }
} 