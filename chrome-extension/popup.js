// chrome-extension/popup.js
document.addEventListener('DOMContentLoaded', () => {
    const statusDot = document.getElementById('statusDot');
    const statusText = document.getElementById('statusText');
    const connectionInfo = document.getElementById('connectionInfo');
    const currentTaskDiv = document.getElementById('currentTask');
    const taskDetails = document.getElementById('taskDetails');
    
    // 获取状态
    chrome.runtime.sendMessage({ type: 'GET_STATUS' }, (response) => {
        updateUI(response);
    });
    
    // 重新连接
    document.getElementById('reconnectBtn').addEventListener('click', () => {
        chrome.runtime.sendMessage({ type: 'RECONNECT' });
        statusText.textContent = '重新连接中...';
    });
    
    // 设置
    document.getElementById('settingsBtn').addEventListener('click', () => {
        chrome.tabs.create({ url: 'options.html' });
    });
    
    function updateUI(status) {
        if (status.connected) {
            statusDot.classList.add('connected');
            statusDot.classList.remove('disconnected');
            statusText.textContent = '已连接服务器';
            connectionInfo.textContent = '等待任务调度...';
        } else {
            statusDot.classList.add('disconnected');
            statusDot.classList.remove('connected');
            statusText.textContent = '未连接';
            connectionInfo.textContent = '点击重新连接';
        }
        
        if (status.currentTask) {
            currentTaskDiv.classList.remove('hidden');
            taskDetails.textContent = `任务: ${status.currentTask.taskId}\n动作: ${status.currentTask.action}`;
        } else {
            currentTaskDiv.classList.add('hidden');
        }
    }
});