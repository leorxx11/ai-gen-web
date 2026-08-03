/**
 * 应用相关常量，与后端 AppConstant / CodeGenTypeEnum 保持一致
 */

// 精选应用的优先级
export const GOOD_APP_PRIORITY = 99

// 默认应用优先级
export const DEFAULT_APP_PRIORITY = 0

// 代码生成类型到展示文案的映射
export const CODE_GEN_TYPE_MAP: Record<string, string> = {
  html: '原生 HTML 模式',
  multi_file: '原生多文件模式',
}

// 格式化代码生成类型
export const formatCodeGenType = (codeGenType?: string) => {
  if (!codeGenType) {
    return '未知类型'
  }
  return CODE_GEN_TYPE_MAP[codeGenType] ?? codeGenType
}
