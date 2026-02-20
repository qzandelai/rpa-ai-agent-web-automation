import { createRouter, createWebHistory } from 'vue-router'
import TaskConfig from '../views/TaskConfig.vue'
import ExecutionMonitor from '../views/ExecutionMonitor.vue'

const routes = [
  { path: '/', redirect: '/task-config' },
  { path: '/task-config', component: TaskConfig, name: 'TaskConfig' },
  { path: '/monitor', component: ExecutionMonitor, name: 'ExecutionMonitor' },
  // 外部链接用导航守卫处理，或直接用 a 标签
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router