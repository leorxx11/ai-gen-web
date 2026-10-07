package com.leo.aigenweb.ai.tools;

import cn.hutool.json.JSONObject;
import com.leo.aigenweb.constant.AppConstant;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具基类，定义所有工具的通用接口
 */
public abstract class BaseTool {

    /**
     * 获取工具的英文名称（对应方法名）
     */
    public abstract String getToolName();

    /**
     * 获取工具的中文显示名称
     */
    public abstract String getDisplayName();

    /**
     * 生成工具请求时的返回值（显示给用户）
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 生成工具执行结果格式（展示给用户并保存到数据库）
     *
     * @param arguments 工具执行参数
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);

    /**
     * 获取应用的项目根目录
     */
    protected Path projectRoot(Long appId) {
        return Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId).toAbsolutePath().normalize();
    }

    /**
     * 将 AI 传入的路径解析为项目目录内的安全路径。
     * 相对路径基于项目根目录解析；绝对路径只有落在项目根目录内才允许。
     * 任何会逃出项目目录的路径（如 ../、越界绝对路径）都会被拒绝，防止 AI 读写、删除项目外的文件。
     *
     * @throws IllegalArgumentException 路径越界
     */
    protected Path resolveSafePath(Long appId, String relativePath) {
        Path root = projectRoot(appId);
        Path input = Paths.get(relativePath == null ? "" : relativePath);
        Path resolved = (input.isAbsolute() ? input : root.resolve(input)).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("路径超出项目目录范围: " + relativePath);
        }
        return resolved;
    }
}
