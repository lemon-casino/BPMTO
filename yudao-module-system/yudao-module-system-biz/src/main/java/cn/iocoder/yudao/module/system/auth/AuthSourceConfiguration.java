package cn.iocoder.yudao.module.system.auth;

import com.xingyuv.justauth.autoconfigure.ExtendProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(1) // 确保这个配置优先级较高
public class AuthSourceConfiguration {

    @Bean
    public ExtendProperties extendProperties() {
        ExtendProperties extendProperties = new ExtendProperties();
        extendProperties.setEnumClass(AuthDingDefaultSource.class);
        return extendProperties;
    }
} 