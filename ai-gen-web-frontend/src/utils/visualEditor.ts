/**
 * 可视化编辑器工具类
 * 负责管理预览 iframe 内的可视化编辑：向 iframe 注入脚本，悬浮高亮元素，点击后把元素信息通过 postMessage 回传。
 *
 * 注意：注入脚本需要读写 iframe 的 document，因此预览页面必须和主站同源（开发环境通过 Vite 代理保证，线上用 Nginx 反向代理）。
 */

export interface ElementInfo {
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
  pagePath: string
  rect: {
    top: number
    left: number
    width: number
    height: number
  }
}

export interface VisualEditorOptions {
  onElementSelected?: (elementInfo: ElementInfo) => void
  onElementHover?: (elementInfo: ElementInfo) => void
}

const SCRIPT_ID = 'visual-edit-script'
const MESSAGE_SOURCE = 'visual-editor'

export class VisualEditor {
  private iframe: HTMLIFrameElement | null = null
  private isEditMode = false
  private readonly options: VisualEditorOptions

  constructor(options: VisualEditorOptions = {}) {
    this.options = options
  }

  /**
   * 绑定预览 iframe
   */
  init(iframe: HTMLIFrameElement | null) {
    this.iframe = iframe
  }

  /**
   * 预览页面是否与主站同源（同源才能注入脚本）
   */
  isSupported(): boolean {
    try {
      return !!this.iframe?.contentDocument?.documentElement
    } catch {
      return false
    }
  }

  get editing() {
    return this.isEditMode
  }

  /**
   * 开启编辑模式，成功返回 true；预览不同源或尚未加载完时返回 false
   */
  enableEditMode(): boolean {
    if (!this.iframe || !this.isSupported()) {
      return false
    }
    this.isEditMode = true
    this.injectEditScript()
    return true
  }

  /**
   * 关闭编辑模式并清除 iframe 内所有编辑状态
   */
  disableEditMode() {
    this.isEditMode = false
    this.sendMessageToIframe({ type: 'TOGGLE_EDIT_MODE', editMode: false })
    this.sendMessageToIframe({ type: 'CLEAR_ALL_EFFECTS' })
  }

  /**
   * 切换编辑模式，返回切换后是否处于编辑模式
   */
  toggleEditMode(): boolean {
    if (this.isEditMode) {
      this.disableEditMode()
    } else {
      this.enableEditMode()
    }
    return this.isEditMode
  }

  /**
   * 清除选中的元素
   */
  clearSelection() {
    this.sendMessageToIframe({ type: 'CLEAR_SELECTION' })
  }

  /**
   * iframe 加载完成时调用：预览刷新后原有注入的脚本会丢失，编辑模式下需要重新注入
   */
  onIframeLoad() {
    if (this.isEditMode) {
      if (!this.injectEditScript()) {
        this.isEditMode = false
      }
    }
  }

  /**
   * 处理来自 iframe 的消息，只接受来自当前预览 iframe 且同源的消息
   */
  handleIframeMessage(event: MessageEvent) {
    if (!this.iframe || event.source !== this.iframe.contentWindow) {
      return
    }
    if (event.origin !== window.location.origin) {
      return
    }
    const payload = event.data
    if (!payload || typeof payload !== 'object' || payload.source !== MESSAGE_SOURCE) {
      return
    }
    switch (payload.type) {
      case 'ELEMENT_SELECTED':
        if (payload.data?.elementInfo) {
          this.options.onElementSelected?.(payload.data.elementInfo as ElementInfo)
        }
        break
      case 'ELEMENT_HOVER':
        if (payload.data?.elementInfo) {
          this.options.onElementHover?.(payload.data.elementInfo as ElementInfo)
        }
        break
    }
  }

  /**
   * 向 iframe 发送消息（仅限同源）
   */
  private sendMessageToIframe(message: Record<string, unknown>) {
    this.iframe?.contentWindow?.postMessage(message, window.location.origin)
  }

  /**
   * 注入编辑脚本到 iframe，成功返回 true
   */
  private injectEditScript(): boolean {
    try {
      const doc = this.iframe?.contentDocument
      if (!doc || !doc.head) {
        return false
      }
      // 已注入过则只需要通知它开启编辑模式
      if (doc.getElementById(SCRIPT_ID)) {
        this.sendMessageToIframe({ type: 'TOGGLE_EDIT_MODE', editMode: true })
        return true
      }
      const scriptElement = doc.createElement('script')
      scriptElement.id = SCRIPT_ID
      scriptElement.textContent = this.generateEditScript()
      doc.head.appendChild(scriptElement)
      return true
    } catch {
      return false
    }
  }

  /**
   * 生成注入 iframe 的脚本内容
   */
  private generateEditScript() {
    return `
      (function() {
        var MESSAGE_SOURCE = '${MESSAGE_SOURCE}';
        var isEditMode = true;
        var currentHoverElement = null;
        var currentSelectedElement = null;
        var listenersAdded = false;

        function injectStyles() {
          if (document.getElementById('edit-mode-styles')) return;
          var style = document.createElement('style');
          style.id = 'edit-mode-styles';
          style.textContent =
            '.edit-hover { outline: 2px dashed #1890ff !important; outline-offset: 2px !important; cursor: crosshair !important; }' +
            '.edit-selected { outline: 3px solid #52c41a !important; outline-offset: 2px !important; cursor: default !important; }' +
            '@keyframes edit-tip-in { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }';
          document.head.appendChild(style);
        }

        function cssEscape(value) {
          return (window.CSS && CSS.escape) ? CSS.escape(value) : String(value).replace(/[^a-zA-Z0-9_-]/g, '\\\\$&');
        }

        // 生成元素选择器：沿父级向上，遇到 id 即停止
        function generateSelector(element) {
          var path = [];
          var current = element;
          while (current && current !== document.body) {
            var selector = current.tagName.toLowerCase();
            if (current.id) {
              path.unshift(selector + '#' + cssEscape(current.id));
              break;
            }
            var cls = typeof current.className === 'string' ? current.className : '';
            var classes = cls.split(/\\s+/).filter(function(c) { return c && c.indexOf('edit-') !== 0; });
            if (classes.length > 0) {
              selector += '.' + classes.map(cssEscape).join('.');
            }
            var siblings = current.parentElement ? Array.prototype.slice.call(current.parentElement.children) : [];
            selector += ':nth-child(' + (siblings.indexOf(current) + 1) + ')';
            path.unshift(selector);
            current = current.parentElement;
          }
          return path.join(' > ');
        }

        function getPagePath() {
          var params = new URLSearchParams(window.location.search);
          params.delete('t');
          var query = params.toString();
          return (query ? '?' + query : '') + window.location.hash;
        }

        function getElementInfo(element) {
          var rect = element.getBoundingClientRect();
          var cls = typeof element.className === 'string'
            ? element.className.split(/\\s+/).filter(function(c) { return c && c.indexOf('edit-') !== 0; }).join(' ')
            : '';
          return {
            tagName: element.tagName,
            id: element.id,
            className: cls,
            textContent: (element.textContent || '').trim().substring(0, 100),
            selector: generateSelector(element),
            // 查询参数和锚点（Vue 工程使用 hash 路由，可据此定位页面），去掉预览用的防缓存参数 t
            pagePath: getPagePath(),
            rect: { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
          };
        }

        function clearHoverEffect() {
          if (currentHoverElement) {
            currentHoverElement.classList.remove('edit-hover');
            currentHoverElement = null;
          }
        }

        function clearSelectedEffect() {
          document.querySelectorAll('.edit-selected').forEach(function(el) { el.classList.remove('edit-selected'); });
          currentSelectedElement = null;
        }

        function isIgnored(target) {
          return !target || target === document.body || target === document.documentElement ||
            target.tagName === 'SCRIPT' || target.tagName === 'STYLE' || target.id === 'edit-tip';
        }

        function addEventListeners() {
          if (listenersAdded) return;
          document.body.addEventListener('mouseover', function(event) {
            if (!isEditMode) return;
            var target = event.target;
            if (target === currentHoverElement || target === currentSelectedElement || isIgnored(target)) return;
            clearHoverEffect();
            target.classList.add('edit-hover');
            currentHoverElement = target;
          }, true);
          document.body.addEventListener('mouseout', function(event) {
            if (!isEditMode) return;
            if (!event.relatedTarget || !event.target.contains(event.relatedTarget)) clearHoverEffect();
          }, true);
          document.body.addEventListener('click', function(event) {
            if (!isEditMode) return;
            // 编辑模式下点击只用于选中，不触发链接跳转等原有行为
            event.preventDefault();
            event.stopPropagation();
            var target = event.target;
            if (isIgnored(target)) return;
            clearSelectedEffect();
            clearHoverEffect();
            target.classList.add('edit-selected');
            currentSelectedElement = target;
            window.parent.postMessage({
              source: MESSAGE_SOURCE,
              type: 'ELEMENT_SELECTED',
              data: { elementInfo: getElementInfo(target) }
            }, window.location.origin);
          }, true);
          listenersAdded = true;
        }

        function showEditTip() {
          if (document.getElementById('edit-tip')) return;
          var tip = document.createElement('div');
          tip.id = 'edit-tip';
          tip.innerHTML = '编辑模式已开启<br/>悬浮查看元素，点击选中元素';
          tip.style.cssText = 'position:fixed;top:20px;right:20px;background:#1890ff;color:#fff;padding:12px 16px;' +
            'border-radius:6px;font-size:14px;z-index:2147483647;box-shadow:0 4px 12px rgba(0,0,0,.15);' +
            'animation:edit-tip-in .3s ease;pointer-events:none;';
          document.body.appendChild(tip);
          setTimeout(function() { tip.remove(); }, 3000);
        }

        window.addEventListener('message', function(event) {
          if (event.source !== window.parent || event.origin !== window.location.origin) return;
          var data = event.data || {};
          switch (data.type) {
            case 'TOGGLE_EDIT_MODE':
              isEditMode = !!data.editMode;
              if (isEditMode) {
                injectStyles();
                addEventListeners();
                showEditTip();
              } else {
                clearHoverEffect();
                clearSelectedEffect();
              }
              break;
            case 'CLEAR_SELECTION':
              clearSelectedEffect();
              break;
            case 'CLEAR_ALL_EFFECTS':
              isEditMode = false;
              clearHoverEffect();
              clearSelectedEffect();
              var tip = document.getElementById('edit-tip');
              if (tip) tip.remove();
              break;
          }
        });

        injectStyles();
        addEventListeners();
        showEditTip();
      })();
    `
  }
}
