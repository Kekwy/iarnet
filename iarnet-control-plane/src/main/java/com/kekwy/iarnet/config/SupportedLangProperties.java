package com.kekwy.iarnet.config;

import com.kekwy.iarnet.enums.Lang;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "iarnet")
public class SupportedLangProperties {

    /** 支持的语言/运行环境列表，对应配置中的 iarnet.supported-lang */
    private List<String> supportedLang = List.of();

}
