package com.kekwy.iarnet;

import com.kekwy.iarnet.config.SupportedLangProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@SpringBootApplication
@EnableConfigurationProperties(SupportedLangProperties.class)
public class MainApplication {
    public static void main(String[] args) {
        org.springframework.boot.SpringApplication.run(MainApplication.class, args);
    }

    @Component
    public static class Initializer implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(Initializer.class);

        @Override
        public void run(ApplicationArguments args) {
            log.info("IARNet 控制面启动完成");
            // 启动时初始化数据库
            // 创建管理员用户
            // 创建默认应用
        }

    }


}
