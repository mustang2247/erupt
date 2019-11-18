package xyz.erupt.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Created by liyuepeng on 2019-10-31.
 */
@Data
@Component
@ConfigurationProperties(prefix = "erupt", ignoreUnknownFields = false)
public class EruptConfig {

    //注解扫描根路径
    private String[] scannerPackage = {"xyz.erupt"};

    private String[] allowRequestFileType = {};

    //文件上传根路径
    private String uploadPath = "/opt/file";

    //是否启动erupt热构建,启动后可以在服务启动时改erupt注解内容，建议生产环境关闭此功能
    private boolean hotBuild = false;

    //是否使用redis管理session
    private boolean redisSession = false;
}