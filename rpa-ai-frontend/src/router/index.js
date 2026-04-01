import { createRouter, createWebHistory } from 'vue-router'
import TaskConfig from '../views/TaskConfig.vue'
import ExecutionMonitor from '../views/ExecutionMonitor.vue'
import RealTimeExecution from '../views/RealTimeExecution.vue'
import CredentialsManage from '../views/CredentialsManage.vue'  // 🆕 新增
import DataDisplay from '../views/DataDisplay.vue'

const routes = [
  { path: '/', redirect: '/task-config' },
  { path: '/task-config', component: TaskConfig, name: 'TaskConfig' },
  { path: '/monitor', component: ExecutionMonitor, name: 'ExecutionMonitor' },
  { path: '/realtime', component: RealTimeExecution, name: 'RealTimeExecution' },
  { path: '/credentials', component: CredentialsManage, name: 'CredentialsManage' }, 
  { path: '/data-display', component: DataDisplay, name: 'DataDisplay',meta: { title: '数据展示' }},
  {
  path: '/knowledge-graph',
  component: () => import('../views/KnowledgeGraph.vue'),
  name: 'KnowledgeGraph',
  meta: { title: '知识图谱' }
  }
]

const router = createRouter({
  history: createWebHistory('/'),
  routes
})

export default router