/// <reference types="vite/client" />

// 自定义环境变量类型声明（见根目录 .env 文件）
interface ImportMetaEnv {
  /** 后端 API 根地址 */
  readonly VITE_API_BASE_URL?: string
  /** 应用生成预览域名 */
  readonly VITE_STATIC_PREVIEW_DOMAIN?: string
  /** 应用部署域名 */
  readonly VITE_DEPLOY_DOMAIN?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
