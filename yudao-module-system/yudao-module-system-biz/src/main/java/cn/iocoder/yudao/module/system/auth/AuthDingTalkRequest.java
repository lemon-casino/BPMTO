package cn.iocoder.yudao.module.system.auth;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSONObject;
import com.xingyuv.jushauth.cache.AuthStateCache;
import com.xingyuv.jushauth.config.AuthConfig;
import com.xingyuv.jushauth.exception.AuthException;
import com.xingyuv.jushauth.model.AuthCallback;
import com.xingyuv.jushauth.model.AuthResponse;
import com.xingyuv.jushauth.model.AuthToken;
import com.xingyuv.jushauth.model.AuthUser;
import com.xingyuv.jushauth.request.AuthDefaultRequest;
import com.xingyuv.jushauth.utils.UrlBuilder;

public class AuthDingTalkRequest extends AuthDefaultRequest {

    public AuthDingTalkRequest(AuthConfig config) {
        super(config, AuthDingDefaultSource.DINGTALK_LEMON);
    }

    public AuthDingTalkRequest(AuthConfig config, AuthStateCache authStateCache) {
        super(config, AuthDingDefaultSource.DINGTALK_LEMON, authStateCache);
    }

    @Override
    public String authorize(String state) {
        return UrlBuilder.fromBaseUrl(source.authorize())
                .queryParam("client_id", config.getClientId())
                .queryParam("response_type", "code")
                .queryParam("scope", "openid")
                .queryParam("state", state)
                .queryParam("redirect_uri", config.getRedirectUri())
                .build();
    }

    @Override
    protected AuthToken getAccessToken(AuthCallback authCallback) {
        // 获取 access_token
        String url = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";
        JSONObject params = new JSONObject();
        params.put("clientId", config.getClientId());
        params.put("clientSecret", config.getClientSecret());
        params.put("code", authCallback.getCode());
        params.put("grantType", "authorization_code");

        String response = HttpRequest.post(url)
                .header("Content-Type", "application/json")
                .body(params.toString())
                .execute()
                .body();
        JSONObject jsonObject = JSONObject.parseObject(response);

        if (ObjectUtil.isNotNull(jsonObject.get("code"))) {
            throw new AuthException(jsonObject.getString("message"));
        }

        return AuthToken.builder()
                .accessToken(jsonObject.getString("accessToken"))
                .build();
    }

    @Override
    protected AuthUser getUserInfo(AuthToken authToken) {
        // 获取用户信息
        String url = "https://api.dingtalk.com/v1.0/contact/users/me";
        System.out.println("dddd"+authToken.getAccessToken());
        String response = HttpRequest.get(url)
                .header("x-acs-dingtalk-access-token", authToken.getAccessToken())
                .execute()
                .body();
        JSONObject jsonObject = JSONObject.parseObject(response);

        if (ObjectUtil.isNotNull(jsonObject.get("code"))) {
            throw new AuthException(jsonObject.getString("message"));
        }

        return AuthUser.builder()
                .uuid(jsonObject.getString("unionId"))
                .token(authToken)
                .source(source.getName())
                .username(jsonObject.getString("nick"))
                .nickname(jsonObject.getString("nick"))
                .avatar(jsonObject.getString("avatarUrl"))
                .email(jsonObject.getString("email"))
                .rawUserInfo(jsonObject)
                .build();
    }



    @Override
    public AuthResponse<AuthUser> login(AuthCallback authCallback) {
        AuthToken token = this.getAccessToken(authCallback);
        AuthUser user = this.getUserInfo(token);
        return AuthResponse.<AuthUser>builder().code(2000).data(user).build();
    }

    @Override
    protected String userInfoUrl(AuthToken authToken) {
        // 参考 https://open.dingtalk.com/document/orgapp/obtain-the-user-information-based-on-the-sns-temporary-authorization
        return StrUtil.format("https://oapi.dingtalk.com/sns/getuserinfo_bycode?accessKey={}&timestamp={}&signature={}",
                config.getClientId(), System.currentTimeMillis(), getSignature());
    }

    private String getSignature() {
        // TODO 后续实现 signature 的生成，参考 https://open.dingtalk.com/document/orgapp/signature-calculation-method
        return "";
    }
} 