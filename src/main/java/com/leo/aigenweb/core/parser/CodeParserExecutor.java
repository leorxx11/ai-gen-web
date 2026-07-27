package com.leo.aigenweb.core.parser;

import com.leo.aigenweb.ai.model.HtmlCodeResult;
import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;

public class CodeParserExecutor {


    private static final HtmlCodeParser  htmlCodeParser = new HtmlCodeParser();
    private static final MultiFileCodeParser  multiFileCodeParser = new MultiFileCodeParser();


    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum){
        return switch (codeGenTypeEnum){
            case HTML ->htmlCodeParser.parseCode(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parseCode(codeContent);
            default -> throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型");
        };
    }

}
