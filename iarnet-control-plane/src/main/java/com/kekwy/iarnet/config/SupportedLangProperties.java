package com.kekwy.iarnet.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@ConfigurationProperties(prefix = "iarnet")
public class SupportedLangProperties {

    /** 支持的语言/运行环境列表，对应配置中的 iarnet.supported-lang */
    private List<String> supportedLang = List.of();

    public void setSupportedLang(List<String> supportedLang) {
        this.supportedLang = supportedLang != null ? supportedLang : List.of();
    }
}
