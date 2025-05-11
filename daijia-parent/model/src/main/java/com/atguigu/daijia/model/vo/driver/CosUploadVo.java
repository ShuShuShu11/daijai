package com.atguigu.daijia.model.vo.driver;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CosUploadVo {

    @Schema(description = "上传路径")  // COS上的路径
    private String url;

    @Schema(description = "回显地址")  // 访问改地址可以直接下载
    private String showUrl;

}