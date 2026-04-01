// background.js - 完整版
console.log('🚀 Background Script 启动');

class AgentWebSocket {
    constructor() {
        this.ws = null;
        this.reconnectInterval = 3000;
        this.heartbeatInterval = null;
        this.isConnected = false;
        this.messageQueue = [];
        this.activeTaskTabs = new Map();
    }

    connect() {
        const serverUrl = 'ws://localhost:8080/rpa-ai/ws/browser-agent';
        const userId = 'anonymous';
        
        console.log('🔌 正在连接:', serverUrl);
        
        if (this.ws) {
            try {
                this.ws.close();
            } catch (e) {}
        }
        
        this.ws = new WebSocket(serverUrl);
        
        this.ws.onopen = (event) => {
            console.log('✅ WebSocket已连接, readyState:', this.ws.readyState);
            this.isConnected = true;
            
            const registerMsg = {
                type: 'REGISTER',
                data: {
                    userId: userId,
                    fingerprint: 'fp_' + Math.random().toString(36).substr(2, 9),
                    userAgent: navigator.userAgent,
                    timestamp: Date.now()
                }
            };
            
            console.log('📤 准备发送注册:', registerMsg);
            
            if (this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify(registerMsg));
                console.log('✅ 注册消息已发送');
            }
            
            this.startHeartbeat();
        };

        this.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                console.log('📨 收到服务器消息:', msg.type, msg);
                
                switch (msg.type) {
                    case 'CONNECTED':
                        console.log('✅ 服务器确认连接:', msg.data);
                        break;
                    case 'REGISTERED':
                        console.log('✅ 注册成功:', msg.data);
                        break;
                    case 'EXECUTE_COMMAND':
                        this.handleExecuteCommand(msg);
                        break;
                    case 'HEARTBEAT_ACK':
                        break;
                    case 'ERROR':
                        console.error('❌ 服务器错误:', msg.data);
                        break;
                    default:
                        console.log('📨 其他消息:', msg.type);
                }
            } catch (e) {
                console.error('❌ 解析消息失败:', e);
            }
        };

        this.ws.onclose = (event) => {
            console.log('🔌 WebSocket断开:', event.code, event.reason);
            this.isConnected = false;
            this.stopHeartbeat();
            setTimeout(() => this.connect(), this.reconnectInterval);
        };

        this.ws.onerror = (error) => {
            console.error('❌ WebSocket错误:', error);
        };
    }

    handleExecuteCommand(msg) {
        console.log('🎯 收到执行指令:', msg);
        const { taskId, stepId, data } = msg;
        
        if (data.action === 'open_url') {
            this.openUrlInNewTab(taskId, stepId, data);
            return;
        }
        
        let targetTabId = this.activeTaskTabs.get(taskId);
        
        if (targetTabId) {
            this.executeOnTab(targetTabId, taskId, stepId, data);
        } else {
            chrome.tabs.query({active: true, currentWindow: true}, (tabs) => {
                if (tabs[0]) {
                    this.activeTaskTabs.set(taskId, tabs[0].id);
                    this.executeOnTab(tabs[0].id, taskId, stepId, data);
                }
            });
        }
    }

    openUrlInNewTab(taskId, stepId, data) {
        const url = data.target || data.value;
        console.log('🌐 在新标签页打开:', url);
        
        chrome.tabs.create({ url: url, active: false }, (tab) => {
            this.activeTaskTabs.set(taskId, tab.id);
            
            chrome.tabs.onUpdated.addListener(function listener(tabId, info) {
                if (tabId === tab.id && info.status === 'complete') {
                    chrome.tabs.onUpdated.removeListener(listener);
                    
                    this.send({
                        type: 'ACTION_RESULT',
                        taskId: taskId,
                        stepId: stepId,
                        data: {
                            success: true,
                            message: `已在新标签页打开: ${url}`,
                            error: null
                        }
                    });
                }
            }.bind(this));
        });
    }

    executeOnTab(tabId, taskId, stepId, data) {
        // 关键修复：只发送到主 frame (frameId: 0)，避免 iframe 中的 content script 干扰
        chrome.tabs.sendMessage(tabId, {
            type: 'EXECUTE_ACTION',
            command: data,
            taskId: taskId,
            stepId: stepId
        }, { frameId: 0 }, (response) => {
            if (chrome.runtime.lastError) {
                console.error('❌ Content script 错误:', chrome.runtime.lastError);
                this.send({
                    type: 'ERROR',
                    taskId: taskId,
                    stepId: stepId,
                    data: { error: chrome.runtime.lastError.message }
                });
            } else {
                console.log('✅ 执行结果:', response);
                this.send({
                    type: 'ACTION_RESULT',
                    taskId: taskId,
                    stepId: stepId,
                    data: {
                        success: response.success ?? true,
                        message: response.message ?? '',
                        error: response.error ?? null
                    }
                });
            }
        });
    }

    startHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
        }
        
        this.heartbeatInterval = setInterval(() => {
            if (this.isConnected && this.ws.readyState === WebSocket.OPEN) {
                this.send({
                    type: 'HEARTBEAT',
                    data: { timestamp: Date.now() }
                });
            }
        }, 30000);
    }

    stopHeartbeat() {
        if (this.heartbeatInterval) {
            clearInterval(this.heartbeatInterval);
            this.heartbeatInterval = null;
        }
    }

    send(message) {
        if (this.isConnected && this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.warn('⚠️ WebSocket未连接，无法发送消息');
        }
    }
}

const agentWS = new AgentWebSocket();
agentWS.connect();

chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
    if (changeInfo.status === 'complete' && agentWS.isConnected) {
        agentWS.send({
            type: 'PAGE_INFO',
            data: {
                url: tab.url,
                title: tab.title,
                tabId: tabId,
                timestamp: Date.now()
            }
        });
    }
});

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'GET_STATUS') {
        sendResponse({
            connected: agentWS.isConnected,
            readyState: agentWS.ws ? agentWS.ws.readyState : -1
        });
    } else if (request.type === 'NEW_TAB') {
        chrome.tabs.create({ url: request.url, active: false }, (tab) => {
            sendResponse({ success: true, tabId: tab.id });
        });
        return true;
    } else if (request.type === 'CAPTURE_SCREENSHOT') {
        chrome.tabs.captureVisibleTab(null, { format: 'png' }, (dataUrl) => {
            if (chrome.runtime.lastError) {
                sendResponse({ error: chrome.runtime.lastError.message });
            } else {
                const base64 = dataUrl.split(',')[1];
                sendResponse({ imageData: base64 });
            }
        });
        return true;
    }
    return true;
});

console.log('✅ Background Script 初始化完成');