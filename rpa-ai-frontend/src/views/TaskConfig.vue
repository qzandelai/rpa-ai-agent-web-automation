<template>
  <el-card class="card">
    <template #header>
      <span class="card-title">🧠 AI智能任务配置</span>
    </template>
    
    <el-form label-width="100px">
      <el-form-item label="任务描述">
        <el-input
          v-model="taskInput"
          type="textarea"
          rows="4"
          placeholder="请输入自然语言任务，例如：打开百度并搜索'Java 17新特性'"
        />
      </el-form-item>
      
      <el-form-item>
        <el-button type="primary" @click="parseTask" :loading="parsing">
          <el-icon><Search /></el-icon> 智能解析
        </el-button>
        <el-button @click="resetForm">
          <el-icon><Refresh /></el-icon> 重置
        </el-button>
      </el-form-item>
    </el-form>
    
    <el-card v-if="parsedTask" class="result-card" style="margin-top: 20px">
      <template #header>
        <span class="card-title">📋 AI解析结果</span>
      </template>
      
      <el-descriptions :column="2" border>
        <el-descriptions-item label="任务ID">{{ parsedTask.id || '未保存' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(parsedTask.status)">
            {{ parsedTask.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ parsedTask.description }}</el-descriptions-item>
      </el-descriptions>
      
      <div v-if="steps.length > 0" style="margin-top: 20px;">
        <h4>📝 执行步骤预览</h4>
        <el-timeline>
          <el-timeline-item
            v-for="step in steps"
            :key="step.stepId"
            :type="getStepType(step.action)"
          >
            <strong>步骤 {{ step.stepId }}: {{ step.description }}</strong>
            <br/>
            <small>操作: {{ step.action }} | 目标: {{ step.target }}</small>
            <small v-if="step.value"> | 值: {{ step.value }}</small>
          </el-timeline-item>
        </el-timeline>
      </div>
      
      <div style="margin-top: 20px; text-align: right;">
        <el-button type="success" @click="saveTask" :loading="saving">
          <el-icon><DocumentAdd /></el-icon> 保存任务
        </el-button>
        <el-button type="warning" @click="executeTask" :loading="executing" :disabled="!parsedTask.id">
          <el-icon><VideoPlay /></el-icon> 立即执行
        </el-button>
      </div>
    </el-card>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { parseTaskAPI, saveTaskAPI, executeTaskAPI } from '@/api/taskApi'
import { Search, Refresh, DocumentAdd, VideoPlay } from '@element-plus/icons-vue'

const taskInput = ref('')
const parsedTask = ref(null)
const parsing = ref(false)
const saving = ref(false)
const executing = ref(false)

const steps = computed(() => {
  if (!parsedTask.value || !parsedTask.value.configJson) return []
  try {
    const config = JSON.parse(parsedTask.value.configJson)
    return config.steps || []
  } catch {
    return []
  }
})

const parseTask = async () => {
  if (!taskInput.value.trim()) {
    ElMessage.warning('请输入任务描述')
    return
  }
  
  parsing.value = true
  try {
    const response = await parseTaskAPI(taskInput.value)
    parsedTask.value = response
    ElMessage.success('🎉 AI解析成功！')
  } catch (error) {
    ElMessage.error('❌ 解析失败: ' + error.message)
  } finally {
    parsing.value = false
  }
}

const saveTask = async () => {
  if (!parsedTask.value) {
    ElMessage.warning('请先解析任务')
    return
  }
  
  saving.value = true
  try {
    const response = await saveTaskAPI(parsedTask.value)
    parsedTask.value = response  // 更新为保存后的数据（包含ID）
    ElMessage.success('✅ 任务保存成功！ID: ' + response.id)
  } catch (error) {
    ElMessage.error('❌ 保存失败: ' + error.message)
  } finally {
    saving.value = false
  }
}

const executeTask = async () => {
  if (!parsedTask.value || !parsedTask.value.id) {
    ElMessage.warning('请先保存任务')
    return
  }
  
  executing.value = true
  ElMessage.info('🚀 开始执行自动化任务，请观察浏览器窗口...')
  
  try {
    const result = await executeTaskAPI(parsedTask.value.id)
    
    if (result.success) {
      ElMessage.success(`✅ 任务执行完成！共执行 ${result.completedSteps || result.stepResults?.length || 0} 步`)
    } else {
      ElMessage.error(`❌ 执行失败: ${result.errorMessage || '未知错误'}`)
    }
  } catch (error) {
    ElMessage.error('❌ 执行异常: ' + error.message)
  } finally {
    executing.value = false
  }
}

const resetForm = () => {
  taskInput.value = ''
  parsedTask.value = null
  ElMessage.info('表单已重置')
}

const getStatusType = (status) => {
  const types = { 'AI_PARSED': 'success', 'FALLBACK_PARSED': 'warning', 'SAVED': 'success' }
  return types[status] || 'info'
}

const getStepType = (action) => {
  const types = { 
    'open_url': 'primary', 
    'click': 'success', 
    'input': 'warning', 
    'wait': 'info',
    'scroll': 'info',
    'extract': 'success'
  }
  return types[action] || 'info'
}
</script>

<style scoped>
.card {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

.card-title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.result-card {
  background: #f5f7fa;
}
</style>