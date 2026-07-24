package com.leo.aigenweb.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.ai.model.HtmlCodeResult;
import com.leo.aigenweb.ai.model.MultiFileCodeResult;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;

import java.io.File;

public class codeFileSaver {

    // 文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + File.separator +"tmp"+ File.separator +"code_output";

    // 单 HTML 保存
    public static File saveHtmlCodeResult(HtmlCodeResult htmlCodeResult){
        String dirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(dirPath, "index.html", htmlCodeResult.getHtmlCode());
        return new File(dirPath);
    }


    // 多文件保存
    public static File saveMultiFileCodeResult(MultiFileCodeResult multiFileCodeResult){
        String dirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(dirPath, "index.html", multiFileCodeResult.getHtmlCode());
        writeToFile(dirPath, "index.css", multiFileCodeResult.getCssCode());
        writeToFile(dirPath, "index.js", multiFileCodeResult.getJsCode());
        return new File(dirPath);
    }


    // 唯一路径生成
    private static String buildUniqueDir(String bizType){
        String uniqueDirName = StrUtil.format("{}_{}", bizType, IdUtil.getSnowflakeNextIdStr());
        String dirPath = FILE_SAVE_ROOT_DIR + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }


    // 文件保存通用方法
    private static void writeToFile(String dirPath, String filename,String content){
        FileUtil.writeUtf8String(content, dirPath + File.separator + filename);
    }
}
