// main.js
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import Vue3SeamlessScroll from 'vue3-seamless-scroll'
import App from './App.vue'
import router from './router'
import 'element-plus/dist/index.css'
import './styles.scss'

const app = createApp(App)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(ElementPlus)
app.use(router)
app.use(Vue3SeamlessScroll)
app.mount('#app')