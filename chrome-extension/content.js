// content.js - 完整版
console.log('✅ Content Script 已加载');

class ActionExecutor {
    constructor() {
        this.observers = new Map();
    }

    async execute(command, taskId, stepId) {
        const { action, target, value } = command;
        console.log(`⚡ 执行动作: ${action}`, { target, value });
        
        try {
            await this.waitForPageStable();
            let result;
            
            switch (action) {
                case 'open_url':
                    result = await this.openUrl(value || target);
                    break;
                case 'click':
                    result = await this.clickElement(target);
                    break;
                case 'click_by_coordinates':
                    const [x, y] = target.split(',').map(Number);
                    result = await this.clickByCoordinates(x, y);
                    break;
                case 'input':
                    result = await this.inputText(target, value);
                    break;
                case 'screenshot':
                    result = await this.takeScreenshot();
                    break;
                case 'wait':
                    result = await this.wait(value);
                    break;
                case 'scroll':
                    result = await this.scroll(target);
                    break;
                case 'extract':
                    result = await this.extractData(target);
                    break;
                case 'get_scroll_position':
                    result = this.getScrollPosition();
                    break;
                case 'get_page_context':
                    result = await this.getPageContext();
                    break;
                case 'submit':
                    result = await this.submitForm(target);
                    break;
                default:
                    throw new Error(`未知动作: ${action}`);
            }
            
            return {
                success: true,
                message: typeof result === 'string' ? result : JSON.stringify(result),
                url: window.location.href,
                imageData: action === 'screenshot' ? result : null
            };
        } catch (error) {
            console.error(`❌ 执行失败:`, error);
            return {
                success: false,
                error: error.message || '未知错误'
            };
        }
    }

    async clickElement(selector) {
        console.log(`🎯 点击操作: ${selector}`);
        
        const selectors = selector.split(',').map(s => s.trim()).filter(s => s);
        let element = null;
        
        for (const sel of selectors) {
            element = document.querySelector(sel);
            if (element) {
                console.log(`✅ 找到元素: ${sel}`);
                break;
            }
        }
        
        // GitHub 搜索框容错
        if (!element && window.location.hostname.includes('github.com')) {
            const githubSelectors = [
                "input[name='q']", ".header-search-input", 
                "#query-builder-test", ".search-input",
                "input[type='text']", "input[placeholder*='Search']"
            ];
            for (const sel of githubSelectors) {
                element = document.querySelector(sel);
                if (element) {
                    console.log(`✅ GitHub 页面找到备用元素: ${sel}`);
                    break;
                }
            }
        }
        
        if (!element && window.location.hostname.includes('baidu.com')) {
            console.log('🔍 百度页面，尝试回车键提交');
            return await this.submitByEnter();
        }
        
        if (!element) {
            throw new Error(`元素未找到: ${selector}`);
        }
        
        try {
            element.scrollIntoView({ behavior: 'instant', block: 'center' });
            await this.sleep(300);
            
            element.click();
            await this.sleep(100);
            
            const rect = element.getBoundingClientRect();
            const events = ['mousedown', 'mouseup', 'click'];
            for (const eventType of events) {
                const event = new MouseEvent(eventType, {
                    bubbles: true,
                    cancelable: true,
                    clientX: rect.left + rect.width/2,
                    clientY: rect.top + rect.height/2,
                    button: 0
                });
                element.dispatchEvent(event);
            }
            
            if (element.type === 'submit' || element.tagName === 'BUTTON') {
                const form = element.closest('form');
                if (form) {
                    const submitEvent = new Event('submit', { bubbles: true, cancelable: true });
                    form.dispatchEvent(submitEvent);
                }
            }
            
            return `已点击: ${selector}`;
        } catch (e) {
            console.warn('点击失败，尝试回车键备选:', e);
            if (window.location.hostname.includes('baidu.com')) {
                return await this.submitByEnter();
            }
            throw e;
        }
    }

    async clickByCoordinates(x, y) {
        console.log(`🖱️ 坐标点击: (${x}, ${y})`);
        
        const element = document.elementFromPoint(x, y);
        if (element) {
            element.click();
        }
        
        const events = ['mousedown', 'mouseup', 'click'];
        for (const eventType of events) {
            const event = new MouseEvent(eventType, {
                bubbles: true,
                cancelable: true,
                clientX: x,
                clientY: y,
                button: 0
            });
            document.elementFromPoint(x, y)?.dispatchEvent(event);
        }
        
        return `已在坐标 (${x}, ${y}) 点击`;
    }

    async submitByEnter() {
        console.log('⌨️ 使用回车键提交搜索');
        
        const input = document.querySelector('#kw, input[name="wd"], input[type="text"]');
        if (!input) {
            throw new Error('找不到输入框，无法使用回车提交');
        }
        
        input.focus();
        await this.sleep(200);
        
        const keyOptions = {
            key: 'Enter',
            code: 'Enter',
            keyCode: 13,
            which: 13,
            bubbles: true,
            cancelable: true
        };
        
        input.dispatchEvent(new KeyboardEvent('keydown', keyOptions));
        input.dispatchEvent(new KeyboardEvent('keypress', keyOptions));
        input.dispatchEvent(new KeyboardEvent('keyup', keyOptions));
        
        const form = input.closest('form');
        if (form) {
            const submitEvent = new Event('submit', { bubbles: true, cancelable: true });
            form.dispatchEvent(submitEvent);
            await this.sleep(100);
            if (window.location.href === 'https://www.baidu.com/' || 
                window.location.href === 'https://www.baidu.com') {
                form.submit();
            }
        }
        
        await this.sleep(500);
        if (window.location.hostname === 'www.baidu.com' && 
            window.location.pathname === '/') {
            const keyword = input.value;
            if (keyword) {
                console.log('🔄 构造搜索URL直接跳转');
                window.location.href = `https://www.baidu.com/s?wd=${encodeURIComponent(keyword)}`;
            }
        }
        
        return '通过回车键/表单提交搜索';
    }

    async inputText(selector, text) {
        const selectors = selector.split(',').map(s => s.trim()).filter(s => s);
        let input = null;
        let usedSelector = '';
        
        for (const sel of selectors) {
            input = document.querySelector(sel);
            if (input) {
                usedSelector = sel;
                console.log(`✅ 找到输入框: ${sel}`);
                break;
            }
        }
        
        // GitHub 登录容错：如果已登录跳转到非登录页，输入框找不到是正常的，视为成功
        if (!input && window.location.hostname.includes('github.com') && !window.location.pathname.includes('/login')) {
            console.log('⏭️ GitHub 已登录，跳过输入步骤');
            return `已跳过输入（当前页面: ${window.location.pathname}）`;
        }
        
        // GitHub 搜索框容错
        if (!input && window.location.hostname.includes('github.com')) {
            const githubSelectors = [
                "input[name='q']", ".header-search-input",
                "#query-builder-test", ".search-input",
                "input[type='text']", "input[placeholder*='Search']",
                "input[aria-label*='Search']"
            ];
            for (const sel of githubSelectors) {
                input = document.querySelector(sel);
                if (input) {
                    usedSelector = sel;
                    console.log(`✅ GitHub 页面找到备用输入框: ${sel}`);
                    break;
                }
            }
        }
        
        // 淘宝特殊处理：如果所有选择器都找不到，尝试通用搜索框
        if (!input && window.location.hostname.includes('taobao.com')) {
            const taobaoSelectors = [
                '#q', "input[name='q']", '.search-combobox-input', 
                '.search-combobox-input-field', '#kw', 
                'input[type="text"]', 'input[type="search"]',
                '.search-input', 'input[placeholder*="搜索"]',
                'input[title*="搜索"]', '.search-combobox-input-wrap input'
            ];
            for (const sel of taobaoSelectors) {
                input = document.querySelector(sel);
                if (input) {
                    usedSelector = sel;
                    console.log(`✅ 淘宝页面找到备用输入框: ${sel}`);
                    break;
                }
            }
        }
        
        if (!input) {
            throw new Error(`输入框未找到: ${selector}`);
        }
        
        input.scrollIntoView({ behavior: 'instant', block: 'center' });
        await this.sleep(300);
        
        // 聚焦输入框
        input.focus();
        input.click();
        await this.sleep(100);
        
        // 清空原有内容
        input.value = '';
        input.dispatchEvent(new Event('input', { bubbles: true }));
        await this.sleep(50);
        
        // 设置新值并触发完整事件链（兼容React/Vue等框架）
        input.value = text;
        
        const events = [
            new Event('focus', { bubbles: true }),
            new Event('beforeinput', { bubbles: true, cancelable: true }),
            new Event('input', { bubbles: true }),
            new KeyboardEvent('keydown', { bubbles: true, cancelable: true }),
            new KeyboardEvent('keyup', { bubbles: true }),
            new Event('change', { bubbles: true })
        ];
        
        for (const event of events) {
            input.dispatchEvent(event);
            await this.sleep(50);
        }
        
        // 淘宝特殊处理：触发 blur 事件，确保搜索建议出现
        if (window.location.hostname.includes('taobao.com')) {
            input.dispatchEvent(new Event('blur', { bubbles: true }));
        }
        
        await this.sleep(200);
        return `已输入: ${text} (选择器: ${usedSelector})`;
    }

    async openUrl(url) {
        return new Promise((resolve, reject) => {
            chrome.runtime.sendMessage({ type: 'NEW_TAB', url }, (response) => {
                if (response?.success) {
                    resolve(`已打开: ${url}`);
                } else {
                    reject(new Error('打开URL失败'));
                }
            });
        });
    }

    async takeScreenshot() {
        console.log('📸 正在截取页面截图');
        
        try {
            const response = await new Promise((resolve, reject) => {
                chrome.runtime.sendMessage({ 
                    type: 'CAPTURE_SCREENSHOT' 
                }, (response) => {
                    if (chrome.runtime.lastError) {
                        reject(chrome.runtime.lastError);
                    } else {
                        resolve(response);
                    }
                });
            });
            
            if (response && response.imageData) {
                return response.imageData;
            } else {
                throw new Error('截图失败');
            }
        } catch (e) {
            console.error('截图失败:', e);
            throw e;
        }
    }

    async wait(seconds) {
        const ms = (parseInt(seconds) || 2) * 1000;
        await this.sleep(ms);
        return `等待 ${ms}ms`;
    }

    async submitForm(selector) {
        console.log(`📝 提交表单: ${selector || '默认表单'}`);
        
        // 如果有选择器，尝试点击提交按钮
        if (selector) {
            const selectors = selector.split(',').map(s => s.trim()).filter(s => s);
            for (const sel of selectors) {
                const btn = document.querySelector(sel);
                if (btn) {
                    btn.click();
                    // 触发完整事件链
                    const rect = btn.getBoundingClientRect();
                    const events = ['mousedown', 'mouseup', 'click'];
                    for (const eventType of events) {
                        const event = new MouseEvent(eventType, {
                            bubbles: true, cancelable: true,
                            clientX: rect.left + rect.width/2,
                            clientY: rect.top + rect.height/2,
                            button: 0
                        });
                        btn.dispatchEvent(event);
                    }
                    const form = btn.closest('form');
                    if (form) {
                        form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
                    }
                    return `已点击提交按钮: ${sel}`;
                }
            }
        }
        
        // 尝试回车提交（找到当前聚焦的输入框或第一个表单）
        const activeInput = document.activeElement;
        if (activeInput && (activeInput.tagName === 'INPUT' || activeInput.tagName === 'TEXTAREA')) {
            const keyOptions = {
                key: 'Enter', code: 'Enter', keyCode: 13, which: 13,
                bubbles: true, cancelable: true
            };
            activeInput.dispatchEvent(new KeyboardEvent('keydown', keyOptions));
            activeInput.dispatchEvent(new KeyboardEvent('keypress', keyOptions));
            activeInput.dispatchEvent(new KeyboardEvent('keyup', keyOptions));
            const form = activeInput.closest('form');
            if (form) form.submit();
            return '通过回车键提交表单';
        }
        
        // 兜底：提交第一个表单
        const firstForm = document.querySelector('form');
        if (firstForm) {
            firstForm.submit();
            return '已提交第一个表单';
        }
        
        throw new Error('未找到可提交的表单或按钮');
    }

    async scroll(direction) {
        const scrollAmount = direction === 'up' ? -500 : 500;
        window.scrollBy(0, scrollAmount);
        return `已滚动: ${direction}`;
    }

    async extractData(selector) {
        const selectors = selector.split(',').map(s => s.trim()).filter(s => s);
        let elements = [];
        let matchedSelector = '';
        
        // 1. 尝试所有提供的选择器
        for (const sel of selectors) {
            const nodeList = document.querySelectorAll(sel);
            if (nodeList.length > 0) {
                elements = Array.from(nodeList);
                matchedSelector = sel;
                console.log(`✅ 找到 ${elements.length} 个提取目标: ${sel}`);
                break;
            }
        }
        
        // 2. 如果没找到，尝试智能兜底（知网、百度学术等）
        if (elements.length === 0) {
            const smartResult = this.smartExtract();
            if (smartResult) {
                console.log('🧠 使用智能提取策略');
                return JSON.stringify(smartResult);
            }
            throw new Error(`提取目标未找到: ${selector}`);
        }
        
        // 3. 如果只有一个元素，返回文本（兼容旧行为）
        if (elements.length === 1) {
            return elements[0].innerText || elements[0].value || '';
        }
        
        // 4. 多个元素：提取结构化数据
        const results = elements.map((el, index) => {
            const item = this.extractItemData(el, index);
            return item;
        }).filter(item => item.title || item.content); // 过滤空数据
        
        if (results.length === 0) {
            throw new Error(`提取目标未找到有效数据: ${selector}`);
        }
        
        console.log(`📊 成功提取 ${results.length} 条结构化数据`);
        return JSON.stringify(results);
    }
    
    // 智能提取：针对常见学术网站的兜底策略
    smartExtract() {
        const hostname = window.location.hostname;
        let candidates = [];
        
        if (hostname.includes('cnki.net')) {
            // 知网常见列表选择器
            candidates = [
                '.result-table-list .item',
                'table.result-table tbody tr',
                '.c_article',
                '.result-list .item',
                '.bd_doc',
                '.essayBox',
                '.list-item'
            ];
        } else if (hostname.includes('baidu.com')) {
            candidates = [
                '.result',
                '.c-container'
            ];
        } else if (hostname.includes('scholar.google')) {
            candidates = [
                '.gs_r',
                '.gs_ri'
            ];
        } else {
            // 通用策略：找可能是列表项的元素
            candidates = [
                '.result-item',
                '.literature-item',
                '.paper-item',
                '.article-item',
                '.search-item'
            ];
        }
        
        for (const sel of candidates) {
            const nodeList = document.querySelectorAll(sel);
            if (nodeList.length > 0) {
                const elements = Array.from(nodeList);
                const results = elements.map((el, index) => this.extractItemData(el, index))
                    .filter(item => item.title || item.content);
                if (results.length > 0) {
                    console.log(`🧠 智能提取命中: ${sel}, 共 ${results.length} 条`);
                    return results;
                }
            }
        }
        
        return null;
    }
    
    // 从单个元素中提取结构化数据
    extractItemData(element, index) {
        const item = { index: index + 1 };
        
        // 尝试提取标题（最常见的位置）
        const titleEl = element.querySelector('a, .title, .c_title, .essay-title, h3, h4, .item-title, .result-title');
        if (titleEl) {
            item.title = titleEl.innerText.trim().replace(/\s+/g, ' ');
            const link = titleEl.getAttribute('href');
            if (link) {
                item.link = link.startsWith('http') ? link : new URL(link, window.location.href).href;
            }
        }
        
        // 尝试提取作者
        const authorEl = element.querySelector('.author, .c_author, .essay-author, .author-info, [class*="author"]');
        if (authorEl) {
            item.authors = authorEl.innerText.trim().replace(/\s+/g, ' ');
        }
        
        // 尝试提取来源/期刊
        const sourceEl = element.querySelector('.source, .c_source, .essay-source, .journal, .pub-info, [class*="source"]');
        if (sourceEl) {
            item.source = sourceEl.innerText.trim().replace(/\s+/g, ' ');
        }
        
        // 尝试提取摘要
        const abstractEl = element.querySelector('.abstract, .c_abstract, .essay-abstract, .summary, [class*="abstract"]');
        if (abstractEl) {
            item.abstract = abstractEl.innerText.trim().replace(/\s+/g, ' ');
        }
        
        // 尝试提取日期
        const dateEl = element.querySelector('.date, .c_date, .year, .pub-date, [class*="date"], [class*="year"]');
        if (dateEl) {
            item.date = dateEl.innerText.trim();
        }
        
        // 如果没有标题，用整个元素的文本作为 content
        if (!item.title) {
            const text = element.innerText.trim().replace(/\s+/g, ' ');
            if (text.length > 0 && text.length < 500) {
                item.title = text;
            } else if (text.length >= 500) {
                item.content = text.substring(0, 500) + '...';
            }
        }
        
        // 清理空字段
        Object.keys(item).forEach(key => {
            if (!item[key] || item[key] === '') {
                delete item[key];
            }
        });
        
        return item;
    }

    getScrollPosition() {
        return { scrollX: window.scrollX, scrollY: window.scrollY };
    }

    getPageContext() {
        try {
            const bodyClone = document.body.cloneNode(true);
            const removeTags = ['script', 'style', 'svg', 'noscript', 'iframe', 'canvas', 'video', 'audio'];
            removeTags.forEach(tag => {
                bodyClone.querySelectorAll(tag).forEach(el => el.remove());
            });
            // 移除注释
            const walker = document.createTreeWalker(bodyClone, NodeFilter.SHOW_COMMENT, null, false);
            const comments = [];
            while (walker.nextNode()) comments.push(walker.currentNode);
            comments.forEach(c => c.remove());

            let html = bodyClone.innerHTML;
            html = html.replace(/>\s+</g, '><').replace(/\s{2,}/g, ' ');
            const maxLen = 3000;
            if (html.length > maxLen) {
                html = html.substring(0, maxLen) + '... [truncated]';
            }

            return {
                url: window.location.href,
                title: document.title,
                html: html
            };
        } catch (e) {
            return {
                url: window.location.href,
                title: document.title,
                html: `<error>${e.message}</error>`
            };
        }
    }

    async sleep(ms) {
        return new Promise(r => setTimeout(r, ms));
    }

    async waitForPageStable() {
        if (document.readyState !== 'complete') {
            await new Promise(r => window.addEventListener('load', r, { once: true }));
        }
        await this.sleep(500);
    }
}

const executor = new ActionExecutor();

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'EXECUTE_ACTION') {
        executor.execute(request.command, request.taskId, request.stepId)
            .then(result => sendResponse(result))
            .catch(error => sendResponse({ success: false, error: error.message }));
        return true;
    }
});

console.log('✅ Content Script 初始化完成');