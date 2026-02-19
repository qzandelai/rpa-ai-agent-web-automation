<template>
  <el-card class="card">
    <template #header>
      <div class="card-header">
        <span class="card-title">📊 执行监控面板</span>
        <div>
          <el-button type="danger" @click="closeBrowser">
            <el-icon><CircleClose /></el-icon> 关闭浏览器
          </el-button>
        </div>
      </div>
    </template>
    
    <el-empty v-if="executionLogs.length === 0" description="暂无执行任务，请在任务配置页面执行任务" />
    
    <div v-else>
      <el-timeline>
        <el-timeline-item
          v-for="log in executionLogs"
          :key="log.id"
          :type="log.type"
          :timestamp="log.time"
        >
          {{ log.message }}
        </el-timeline-item>
      </el-timeline>
      
      <el-divider />
      
      <div v-if="lastResult">
        <h4>最后执行结果</h4>
        <el-alert
          :title="lastResult.success ? '执行成功' : '执行失败'"
          :type="lastResult.success ? 'success' : 'error'"
          :description="lastResult.errorMessage || `共执行 ${lastResult.stepResults?.length || 0} 步`"
          show-icon
        />
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CircleClose } from '@element-plus/icons-vue'
import { closeBrowserAPI } from '@/api/taskApi'

const executionLogs = ref([])
const lastResult = ref(null)

// 添加日志（供其他页面调用）
const addLog = (message, type = 'info') => {
  executionLogs.value.push({
    id: Date.now(),
    message,
    type,
    time: new Date().toLocaleTimeString()
  })
}

const closeBrowser = async () => {
  try {
    await closeBrowserAPI()
    ElMessage.success('浏览器已关闭')
    addLog('手动关闭浏览器', 'warning')
  } catch (error) {
    ElMessage.error('关闭失败: ' + error.message)
  }
}

// 暴露方法供外部调用
defineExpose({
  addLog,
  setResult: (result) => { lastResult.value = result }
})
</script>

<style scoped>
.card {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}
</style>