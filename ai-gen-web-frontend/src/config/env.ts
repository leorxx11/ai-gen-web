/**
 * 全局环境配置：域名统一从环境变量读取（见根目录 .env 文件），部署时无需改代码
 */

// 后端 API 根地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 应用生成预览域名
export const STATIC_PREVIEW_DOMAIN =
  import.meta.env.VITE_STATIC_PREVIEW_DOMAIN || `${API_BASE_URL}/static`

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost:8080'

// 获取生成应用的静态资源预览地址（{codeGenType}_{appId} 目录对应一个生成的网站）
export const getStaticPreviewUrl = (codeGenType: string, appId: string | number) => {
  return `${STATIC_PREVIEW_DOMAIN}/${codeGenType}_${appId}/`
}

// 获取应用部署后的访问地址
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}
