// Implements System
import eslintPluginVue from 'eslint-plugin-vue';
import tsParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';

export default [
  {
    files: ['**/*.ts', '**/*.vue'],
    languageOptions: {
      parser: tsParser
    },
    plugins: {
      vue: eslintPluginVue,
      '@typescript-eslint': tsPlugin
    },
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  }
];