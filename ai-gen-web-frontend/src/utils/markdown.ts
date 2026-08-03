import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import json from 'highlight.js/lib/languages/json'

/**
 * 全局共享的 markdown-it 实例，支持 HTML / CSS / JavaScript 等代码高亮
 */

// 按需注册语言，避免打包完整的 highlight.js（HTML 使用 xml 语法）
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('html', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('json', json)

export const md = new MarkdownIt({
  // 禁止渲染消息中的原始 HTML，防止 XSS
  html: false,
  linkify: true,
  breaks: true,
})

// 流式输出过程中使用的轻量实例：不做代码高亮，把每次重渲染的开销降到最低
export const mdPlain = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
})

// 高亮函数单独设置：函数体内引用 md.utils 做兜底转义，避免初始化时的循环引用
md.set({
  highlight: (code, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        const highlighted = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
        return `<pre class="hljs"><code>${highlighted}</code></pre>`
      } catch {
        // 高亮失败时退回转义展示
      }
    }
    return `<pre class="hljs"><code>${md.utils.escapeHtml(code)}</code></pre>`
  },
})
