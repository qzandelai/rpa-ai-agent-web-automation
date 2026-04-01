<template>
  <div class="task-config-container">
    <el-card class="main-card">
      <template #header>
        <div class="card-header">
          <span class="title">🧠 AI智能任务配置</span>
          <el-tag v-if="connectionStatus === 'connected'" type="success">系统正常</el-tag>
          <el-tag v-else type="danger">系统离线</el-tag>
        </div>
      </template>
      
      <el-form label-width="120px" class="task-form">
        <!-- 任务描述 -->
        <el-form-item label="任务描述" required>
          <el-input
            v-model="taskInput"
            type="textarea"
            :rows="4"
            placeholder="例如：登录github、搜索Java教程等。&#10;AI将自动解析为执行步骤"
          />
        </el-form-item>
        
        <!-- 凭据选择 -->
        <el-form-item label="账号密码">
          <div class="credential-select">
            <el-select 
              v-model="selectedCredentials" 
              placeholder="选择已保存的凭据（登录任务需要）" 
              clearable
              style="width: 320px"
            >
              <el-option
                v-for="cred in credentialsList"
                :key="cred.id"
                :label="`${cred.credentialName} (${cred.username})`"
                :value="cred.id"
              />
            </el-select>
            <el-button link type="primary" @click="goToCredentials">
              <el-icon><Setting /></el-icon> 管理凭据
            </el-button>
          </div>
        </el-form-item>
        
        <!-- 操作按钮 -->
        <el-form-item>
          <el-button type="primary" @click="parseTask" :loading="parsing" size="large">
            <el-icon><Search /></el-icon> 智能解析
          </el-button>
          <el-button @click="resetForm" size="large">
            <el-icon><Refresh /></el-icon> 重置
          </el-button>
        </el-form-item>
      </el-form>
      
      <!-- 解析结果展示 -->
      <el-card v-if="parsedTask" class="result-card" shadow="hover">
        <template #header>
          <div class="result-header">
            <span class="card-title">📋 AI解析结果</span>
            <div>
              <el-tag :type="getStatusType(parsedTask.status)" effect="dark">
                {{ parsedTask.status }}
              </el-tag>
            </div>
          </div>
        </template>
        
        <el-descriptions :column="2" border>
          <el-descriptions-item label="任务ID">{{ parsedTask.id || '未保存' }}</el-descriptions-item>
          <el-descriptions-item label="任务名称">{{ parsedTask.taskName }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ parsedTask.description }}</el-descriptions-item>
          
          <el-descriptions-item label="凭据" :span="2" v-if="parsedTask.credentialsId">
            <el-tag type="success" effect="plain">
              <el-icon><Lock /></el-icon> 使用保存的凭据 (ID: {{ parsedTask.credentialsId }})
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <!-- ==================== 步骤预览 + 图片上传 ==================== -->
        <div v-if="steps.length > 0" class="steps-section">
          <div class="section-title">
            <h4>📝 执行步骤预览</h4>
            <el-tooltip content="为容易定位失败的元素（如按钮）上传截图，当CSS选择器失效时将使用AI图像识别定位" placement="top">
              <el-icon><QuestionFilled /></el-icon>
            </el-tooltip>
          </div>
          
          <el-timeline>
            <el-timeline-item
              v-for="step in steps"
              :key="step.stepId"
              :type="getStepType(step.action)"
              :hollow="true"
            >
              <div class="step-card">
                <div class="step-header">
                  <div class="step-info">
                    <div class="step-title">
                      <span class="step-num">步骤 {{ step.stepId }}</span>
                      <span class="step-desc">{{ step.description }}</span>
                    </div>
                    <div class="step-detail">
                      <el-tag size="small" effect="plain" class="action-tag">{{ step.action }}</el-tag>
                      <code class="target-code">{{ step.target }}</code>
                      <span v-if="step.value && !step.value.includes('{{')" class="value-text">
                        → {{ step.value.length > 20 ? step.value.substring(0, 20) + '...' : step.value }}
                      </span>
                      <el-tag v-if="step.value && step.value.includes('{{')" size="small" type="warning" effect="dark">
                        凭据占位符
                      </el-tag>
                    </div>
                  </div>
                  
                  <!-- 图片上传按钮（仅click操作显示） -->
                  <div v-if="step.action === 'click'" class="upload-area">
                    <el-upload
                      action="#"
                      :auto-upload="false"
                      :show-file-list="false"
                      :on-change="(file) => handleImageUpload(file, step)"
                      accept="image/png,image/jpeg,image/jpg"
                      class="image-uploader"
                    >
                      <el-button 
                        size="small" 
                        :type="step.imageTemplate ? 'success' : 'primary'"
                        plain
                      >
                        <el-icon><Picture /></el-icon>
                        {{ step.imageTemplate ? '更换模板' : '上传定位图' }}
                      </el-button>
                    </el-upload>
                  </div>
                </div>

                <!-- 已上传图片预览 -->
                <div v-if="step.imageTemplate" class="image-preview-section">
                  <div class="preview-container">
                    <el-image 
                      :src="'data:image/png;base64,' + step.imageTemplate" 
                      class="template-image"
                      fit="contain"
                      :preview-src-list="['data:image/png;base64,' + step.imageTemplate]"
                    />
                    <div class="image-actions">
                      <el-tag size="small" type="success" effect="dark">
                        <el-icon><Check /></el-icon> 已启用视觉定位
                      </el-tag>
                      <el-button 
                        link 
                        type="danger" 
                        size="small" 
                        @click="removeImage(step)"
                      >
                        <el-icon><Delete /></el-icon> 移除
                      </el-button>
                    </div>
                  </div>
                  <div class="image-tips">
                    <el-icon><InfoFilled /></el-icon>
                    当CSS选择器失效时，将使用此图片进行AI视觉定位（匹配阈值：{{ (step.imageThreshold || 0.8) * 100 }}%）
                  </div>
                </div>
                
                <!-- 提示信息（未上传时） -->
                <div v-else-if="step.action === 'click'" class="upload-tip">
                  <el-text type="info" size="small">
                    <el-icon><Picture /></el-icon>
                    建议上传按钮截图作为备用定位方式
                  </el-text>
                </div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>

        <!-- 操作按钮 -->
        <div class="action-bar">
          <el-button type="success" @click="saveTask" :loading="saving" size="large">
            <el-icon><DocumentAdd /></el-icon> 保存任务
          </el-button>
          <el-button type="warning" @click="executeTask" :loading="executing" :disabled="!parsedTask.id" size="large">
            <el-icon><VideoPlay /></el-icon> 立即执行
          </el-button>
        </div>
      </el-card>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { 
  Search, 
  Refresh, 
  DocumentAdd, 
  VideoPlay, 
  Setting, 
  Lock,
  Picture,
  Check,
  Delete,
  InfoFilled,
  QuestionFilled
} from '@element-plus/icons-vue'

const router = useRouter()
const API_BASE = '/api'

// ============ 数据状态 ============
const taskInput = ref('')
const selectedCredentials = ref(null)
const credentialsList = ref([])
const parsing = ref(false)
const saving = ref(false)
const executing = ref(false)
const parsedTask = ref(null)
const connectionStatus = ref('checking')

// ============ 计算属性 ============
const steps = computed(() => {
  if (!parsedTask.value || !parsedTask.value.configJson) return []
  try {
    const config = JSON.parse(parsedTask.value.configJson)
    return config.steps || []
  } catch (e) {
    console.error('解析步骤失败:', e)
    return []
  }
})

// ============ 生命周期 ============
onMounted(async () => {
  await loadCredentials()
  checkHealth()
})

// ============ 方法 ============

// 健康检查
const checkHealth = async () => {
  try {
    const res = await fetch(`${API_BASE}/tasks/health`)
    if (res.ok) {
      connectionStatus.value = 'connected'
    }
  } catch (e) {
    connectionStatus.value = 'disconnected'
  }
}

// 加载凭据列表
const loadCredentials = async () => {
  try {
    const res = await fetch(`${API_BASE}/credentials/list`)
    credentialsList.value = await res.json()
  } catch (e) {
    console.error('加载凭据失败:', e)
  }
}

// 跳转到凭据管理
const goToCredentials = () => {
  router.push('/credentials')
}

// ==================== 图片上传处理 ====================

const handleImageUpload = (file, step) => {
  // 校验文件类型
  const isImage = file.raw.type === 'image/jpeg' || file.raw.type === 'image/png' || file.raw.type === 'image/jpg'
  if (!isImage) {
    ElMessage.error('只支持 JPG/PNG 格式的图片!')
    return false
  }
  
  // 校验文件大小（限制500KB，base64会膨胀约33%）
  const isLt500K = file.raw.size / 1024 < 500
  if (!isLt500K) {
    ElMessage.error('图片大小不能超过 500KB，请压缩后重新上传!')
    return false
  }

  // 读取为base64
  const reader = new FileReader()
  reader.onload = (e) => {
    // 去掉 data:image/png;base64, 前缀，只保留纯base64数据
    const base64 = e.target.result.split(',')[1]
    step.imageTemplate = base64
    step.imageThreshold = 0.8  // 默认匹配阈值（80%相似度）
    ElMessage.success(`步骤${step.stepId}已添加图像定位模板，系统将优先使用CSS选择器，失效时自动启用视觉定位`)
  }
  reader.onerror = () => {
    ElMessage.error('图片读取失败')
  }
  reader.readAsDataURL(file.raw)
  
  return false // 阻止自动上传
}

// 移除图片
const removeImage = (step) => {
  step.imageTemplate = null
  step.imageThreshold = null
  ElMessage.info('已移除图像模板，该步骤将仅使用CSS选择器定位')
}

// 解析任务
const parseTask = async () => {
  if (!taskInput.value.trim()) {
    ElMessage.warning('请输入任务描述')
    return
  }
  
  parsing.value = true
  try {
    const res = await fetch(`${API_BASE}/tasks/parse`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        description: taskInput.value,
        credentialsId: selectedCredentials.value
      })
    })
    
    if (!res.ok) throw new Error('解析失败')
    
    parsedTask.value = await res.json()
    ElMessage.success('🎉 任务解析成功！可为关键步骤上传图像模板以提高执行成功率')
  } catch (error) {
    ElMessage.error('❌ 解析失败: ' + error.message)
  } finally {
    parsing.value = false
  }
}

// 保存任务（包含图片数据）
const saveTask = async () => {
  if (!parsedTask.value) {
    ElMessage.warning('请先解析任务')
    return
  }

  // 检查是否有上传图片，给用户提示
  const stepsWithImage = steps.value.filter(s => s.imageTemplate).length
  if (stepsWithImage > 0) {
    console.log(`📸 任务包含 ${stepsWithImage} 个图像定位模板，将随任务配置一起保存`)
  }
  
  saving.value = true
  try {
    // 关键：确保configJson包含最新的imageTemplate数据
    // 由于steps是计算属性，parsedTask.configJson需要重新构建
    const configObj = JSON.parse(parsedTask.value.configJson)
    configObj.steps = steps.value  // 使用当前steps（包含imageTemplate）
    parsedTask.value.configJson = JSON.stringify(configObj)
    
    const res = await fetch(`${API_BASE}/tasks/save`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(parsedTask.value)
    })
    
    if (!res.ok) throw new Error('保存失败')
    
    const saved = await res.json()
    parsedTask.value.id = saved.id
    ElMessage.success(`✅ 任务保存成功！ID: ${saved.id} ${stepsWithImage > 0 ? '(含' + stepsWithImage + '个图像模板)' : ''}`)
  } catch (error) {
    ElMessage.error('❌ 保存失败: ' + error.message)
  } finally {
    saving.value = false
  }
}

// 执行任务
const executeTask = async () => {
  if (!parsedTask.value?.id) {
    ElMessage.warning('请先保存任务')
    return
  }
  
  executing.value = true
  ElMessage.info('🚀 开始执行，请观察浏览器窗口...')
  
  try {
    const res = await fetch(`${API_BASE}/execution/task/${parsedTask.value.id}`, {
      method: 'POST',
      signal: AbortSignal.timeout(60000)
    })
    
    if (!res.ok) throw new Error('执行请求失败')
    
    const result = await res.json()
    if (result.executionId) {
      ElMessage.success('✅ 任务已提交，正在执行...')
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      ElMessage.warning('⏱️ 请求超时，但任务可能仍在执行，请查看执行监控页面')
    } else {
      ElMessage.error('❌ 执行异常: ' + error.message)
    }
  } finally {
    executing.value = false
  }
}

const resetForm = () => {
  taskInput.value = ''
  selectedCredentials.value = null
  parsedTask.value = null
  ElMessage.info('表单已重置')
}

const getStatusType = (status) => {
  const types = { 'AI_PARSED': 'success', 'FALLBACK_PARSED': 'warning', 'SAVED': 'info' }
  return types[status] || 'info'
}

const getStepType = (action) => {
  const types = { 
    'open_url': 'primary', 
    'click': 'success', 
    'input': 'warning', 
    'wait': 'info'
  }
  return types[action] || 'info'
}
</script>

<style scoped>
.task-config-container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.main-card {
  border-radius: 12px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: bold;
  color: #303133;
}

.task-form {
  margin-top: 20px;
}

.credential-select {
  display: flex;
  align-items: center;
  gap: 10px;
}

.result-card {
  margin-top: 24px;
  background: #fafafa;
  border-radius: 8px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-weight: 600;
  color: #303133;
}

.steps-section {
  margin-top: 24px;
  padding: 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: #606266;
}

.section-title h4 {
  margin: 0;
  color: #303133;
}

.step-card {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.step-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.step-info {
  flex: 1;
}

.step-title {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.step-num {
  font-weight: bold;
  color: #409eff;
  font-size: 14px;
}

.step-desc {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}

.step-detail {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.action-tag {
  text-transform: uppercase;
  font-weight: 600;
}

.target-code {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  color: #666;
}

.value-text {
  color: #67c23a;
  font-size: 13px;
}

.upload-area {
  margin-left: 16px;
}

.image-preview-section {
  margin-top: 12px;
  padding: 12px;
  background: #fff;
  border-radius: 6px;
  border: 1px dashed #d9ecff;
}

.preview-container {
  display: flex;
  align-items: center;
  gap: 16px;
}

.template-image {
  width: 120px;
  height: 80px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #fafafa;
}

.image-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.image-tips {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
  display: flex;
  align-items: center;
  gap: 4px;
}

.upload-tip {
  margin-top: 8px;
  padding: 8px;
  background: #f4f4f5;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-bar {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

:deep(.el-timeline-item__node) {
  background-color: #409eff;
}
</style>