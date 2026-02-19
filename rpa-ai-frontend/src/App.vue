<template>
  <el-container style="height: 100vh">
    <el-header style="background: #409EFF; color: white; display: flex; align-items: center; padding: 0 20px">
      <h2>🚀 RPA+AI Agent 网页自动化任务执行器</h2>
    </el-header>
    <el-container>
      <el-aside width="200px" style="background: #f0f2f5">
        <el-menu router :default-active="$route.path" style="border-right: none">
          <el-menu-item index="/task-config">
            <el-icon><Setting /></el-icon>
            <span>任务配置</span>
          </el-menu-item>
          <el-menu-item index="/monitor">
            <el-icon><Monitor /></el-icon>
            <span>执行监控</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="padding: 20px; background: #fafafa">
        <router-view></router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { Setting, Monitor } from '@element-plus/icons-vue'
</script>