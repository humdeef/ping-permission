import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

/**
 * ping-web 的 ESLint 配置（flat config）。
 *
 * 只启用「能挡住运行时错误与 Vue 常见坑」的规则（未定义变量、漏 :key、
 * 直接改 props、未处理的 Promise 等），不启用排版类规则：
 * 仓库还没有引入 Prettier，排版规则会产生大量无意义告警。
 * 以后接入 Prettier 时，可换成 `flat/recommended` + `eslint-config-prettier`。
 */
export default [
  {
    name: 'ping-web/ignores',
    ignores: ['dist/**', 'node_modules/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/essential'],
  {
    name: 'ping-web/language-options',
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
  },
  {
    name: 'ping-web/rules',
    rules: {
      // 视图组件按文件名注册（Users.vue、Roles.vue），沿用单词命名
      'vue/multi-word-component-names': 'off',
      // 允许用 _ 前缀标记“协议要求但未使用”的参数
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      // 禁止遗留调试输出；warn/error 用于运行时告警
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      // v-html 只有在确认内容可信时才使用，默认给出提醒
      'vue/no-v-html': 'warn',
    },
  },
]
