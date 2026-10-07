# ai-gen-web-frontend

This template should help get you started developing with Vue 3 in Vite.

## Recommended IDE Setup

[VS Code](https://code.visualstudio.com/) + [Vue (Official)](https://marketplace.visualstudio.com/items?itemName=Vue.volar) (and disable Vetur).

## Recommended Browser Setup

- Chromium-based browsers (Chrome, Edge, Brave, etc.):
  - [Vue.js devtools](https://chromewebstore.google.com/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd)
  - [Turn on Custom Object Formatter in Chrome DevTools](http://bit.ly/object-formatters)
- Firefox:
  - [Vue.js devtools](https://addons.mozilla.org/en-US/firefox/addon/vue-js-devtools/)
  - [Turn on Custom Object Formatter in Firefox DevTools](https://fxdx.dev/firefox-devtools-custom-object-formatters/)

## Type Support for `.vue` Imports in TS

TypeScript cannot handle type information for `.vue` imports by default, so we replace the `tsc` CLI with `vue-tsc` for type checking. In editors, we need [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar) to make the TypeScript language service aware of `.vue` types.

## Customize configuration

See [Vite Configuration Reference](https://vite.dev/config/).

## Project Setup

```sh
npm install
```

### Compile and Hot-Reload for Development

```sh
npm run dev
```

### Type-Check, Compile and Minify for Production

```sh
npm run build
```

### Lint with [ESLint](https://eslint.org/)

```sh
npm run lint
```

## 可视化编辑与同源代理

可视化编辑需要向预览 iframe 注入脚本并读取被点击的元素，这要求**预览页面与主站同源**：

- 开发环境：`.env.development` 把 `VITE_API_BASE_URL` / `VITE_STATIC_PREVIEW_DOMAIN` 设为相对路径（`/api`、`/api/static`），由 `vite.config.ts` 的 `server.proxy`（`preview.proxy` 同理）转发到后端。后端地址默认 `http://localhost:8123`，可用环境变量 `VITE_PROXY_TARGET` 覆盖。
- 线上：用 Nginx 把 `/api` 反向代理到后端，并保证前端构建时使用相对路径。
- 如果预览与主站不同源，编辑按钮点击后会提示无法进入编辑模式，不影响其他功能。
