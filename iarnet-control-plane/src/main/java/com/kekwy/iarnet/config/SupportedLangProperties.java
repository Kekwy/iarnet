package com.kekwy.iarnet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "iarnet")
public class SupportedLangProperties {

    /** 支持的语言/运行环境列表，对应配置中的 iarnet.supported-lang */
    private List<String> supportedLang = List.of();

}
