<template>
  <div class="app-container">
    <!-- 科技背景 -->
    <div class="tech-bg"></div>
    <div class="grid-bg" style="position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: -1;"></div>
    <div class="scan-line"></div>

    <!-- 顶部导航 -->
    <header class="tech-header">
      <div class="logo-section">
        <div class="logo-icon">
          <el-icon size="32" color="#00d4ff"><Connection /></el-icon>
        </div>
        <div class="logo-text">
          <h1>RPA-AI <span class="tech-text">AGENT</span></h1>
          <p class="subtitle">智能网页自动化任务执行器</p>
        </div>
      </div>
      
      <div class="system-status">
        <div class="status-item">
          <span class="status-dot" :class="wsConnected ? 'active' : 'inactive'"></span>
          <span>系统连接</span>
        </div>
        <div class="status-item">
          <el-icon><Timer /></el-icon>
          <span>{{ currentTime }}</span>
        </div>
      </div>
    </header>

    <!-- 主布局 -->
    <div class="main-layout">
      <!-- 侧边导航 -->
      <nav class="tech-sidebar">
        <div class="nav-menu">
          <router-link 
            v-for="item in menuItems" 
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ active: $route.path === item.path }"
          >
            <el-icon size="20"><component :is="item.icon" /></el-icon>
            <span>{{ item.name }}</span>
            <div class="nav-glow"></div>
          </router-link>
        </div>
        
        <!-- 系统信息 -->
        <div class="sys-info">
          <div class="info-item">
            <span class="label">版本</span>
            <span class="value">v2.0.0</span>
          </div>
          <div class="info-item">
            <span class="label">节点</span>
            <span class="value">Node-01</span>
          </div>
        </div>
      </nav>

      <!-- 内容区 -->
      <main class="content-area">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { 
  Connection, 
  Timer, 
  Setting, 
  Monitor, 
  VideoPlay, 
  Lock, 
  DataLine,
  Share,
} from '@element-plus/icons-vue'
import { taskWS } from '@/api/websocket'

const route = useRoute()
const currentTime = ref('')
const wsConnected = ref(false)

const menuItems = [
  { path: '/task-config', name: '任务配置', icon: Setting },
  { path: '/realtime', name: '实时监控', icon: VideoPlay },
  { path: '/monitor', name: '执行日志', icon: DataLine },
  { path: '/data-display', name: '采集数据', icon: DataLine },
  { path: '/credentials', name: '凭据管理', icon: Lock },
  { path: '/knowledge-graph', name: '知识图谱', icon: Share },
]

// 时间更新
const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    hour12: false,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// WebSocket状态监控
onMounted(() => {
  updateTime()
  const timer = setInterval(updateTime, 1000)
  
  // 连接WebSocket监听状态
  taskWS.connect()
  taskWS.on('connected', () => {
    wsConnected.value = true
  })
  taskWS.on('error', () => {
    wsConnected.value = false
  })
  
  onUnmounted(() => {
    clearInterval(timer)
  })
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.tech-header {
  height: 70px;
  background: rgba(10, 14, 26, 0.95);
  backdrop-filter: blur(20px);
  border-bottom: 1px solid rgba(0, 212, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 30px;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
}

.logo-section {
  display: flex;
  align-items: center;
  gap: 15px;
}

.logo-icon {
  width: 50px;
  height: 50px;
  background: linear-gradient(135deg, rgba(0, 212, 255, 0.2), rgba(112, 0, 255, 0.2));
  border: 1px solid var(--tech-primary);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--tech-glow);
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-5px); }
}

.logo-text h1 {
  margin: 0;
  font-size: 24px;
  color: #fff;
  letter-spacing: 2px;
}

.subtitle {
  margin: 0;
  font-size: 12px;
  color: var(--tech-text-secondary);
  letter-spacing: 1px;
}

.system-status {
  display: flex;
  align-items: center;
  gap: 30px;
}

.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--tech-text-secondary);
  font-size: 14px;
}

.main-layout {
  display: flex;
  margin-top: 70px;
  height: calc(100vh - 70px);
}

.tech-sidebar {
  width: 260px;
  background: rgba(16, 24, 48, 0.6);
  backdrop-filter: blur(20px);
  border-right: 1px solid rgba(0, 212, 255, 0.1);
  display: flex;
  flex-direction: column;
  padding: 20px 0;
}

.nav-menu {
  flex: 1;
  padding: 0 15px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 15px 20px;
  margin-bottom: 8px;
  border-radius: 8px;
  color: var(--tech-text-secondary);
  text-decoration: none;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
  border: 1px solid transparent;
}

.nav-item:hover {
  background: rgba(0, 212, 255, 0.1);
  color: var(--tech-primary);
  border-color: rgba(0, 212, 255, 0.3);
}

.nav-item.active {
  background: linear-gradient(90deg, rgba(0, 212, 255, 0.2), transparent);
  color: var(--tech-primary);
  border-color: var(--tech-primary);
  box-shadow: 0 0 20px rgba(0, 212, 255, 0.2);
}

.nav-glow {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 0;
  background: var(--tech-primary);
  box-shadow: 0 0 10px var(--tech-primary);
  transition: height 0.3s;
}

.nav-item.active .nav-glow {
  height: 60%;
}

.sys-info {
  padding: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin-top: auto;
}

.info-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 12px;
}

.info-item .label {
  color: var(--tech-text-secondary);
}

.info-item .value {
  color: var(--tech-primary);
  font-family: monospace;
}

.content-area {
  flex: 1;
  overflow: auto;
  padding: 30px;
  position: relative;
}

/* 路由切换动画 */
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}

.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-20px);
}

.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(20px);
}
</style>