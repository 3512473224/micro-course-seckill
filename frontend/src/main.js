import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 学术风主题必须在 element-plus 样式之后引入，才能覆盖 CSS 变量
import './styles/theme.scss'
import App from './App.vue'
import router from './router'
import { loadUser } from './store/user'

// 应用启动时从 localStorage 恢复登录态
loadUser()

const app = createApp(App)
app.use(router)
app.use(ElementPlus)
app.mount('#app')
