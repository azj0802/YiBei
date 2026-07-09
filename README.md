# 一贝 (YiBei) — 健康生活助手

一款 Android 健康管理应用，支持手机 + 智能手表双端，集成 AI 智能分析，帮助用户追踪喝水、睡眠、步数等日常健康数据。

## 功能特性

### 📱 手机端（app 模块）

- **喝水记录** — 自定义每日饮水目标，记录每次饮水量，可视化水杯动画
- **日程管理** — 查看月历日程，添加/管理赛事与日程安排
- **健康数据** — 步数统计、睡眠监测（含睡眠分期）、卡路里消耗，支持日历翻页查看历史数据
- **AI 分析** — 接入 DeepSeek 大模型，基于个人健康数据生成智能分析报告与建议
- **周报总览** — 每周喝水、睡眠、步数趋势图表与 AI 周报
- **个人中心** — 用户登录/注册、头像设置、会员入口、主题切换、多语言支持（中/英）
- **开屏动画** — 水杯液面波动矢量动效

### ⌚ 手表端（wear 模块）

- 轻量版健康数据展示，适配 Wear OS 设备

## 技术栈

| 项目 | 说明 |
|------|------|
| 语言 | Kotlin |
| 最低版本 | Android 8.0（API 26）/ 手表端 Android 11（API 30）|
| 目标版本 | Android 14（API 34）|
| 编译 JDK | Java 17 |
| UI 框架 | XML Layout + ViewBinding |
| 异步处理 | Kotlin Coroutines |
| UI 组件 | Material Design 3、CardView、RecyclerView |
| AI 接口 | DeepSeek Chat API |
| 数据存储 | SharedPreferences |

## 项目结构

```
yibei/
├── app/                    # 手机端主模块
│   └── src/main/
│       ├── java/com/example/yibei/
│       │   ├── data/           # 数据层（健康数据、模拟数据、用户管理）
│       │   ├── ui/             # 界面层
│       │   │   ├── auth/       # 登录/注册
│       │   │   ├── onboarding/ # 新手引导
│       │   │   └── utils/      # UI 工具类
│       │   ├── MainActivity.kt
│       │   ├── SplashActivity.kt
│       │   └── LocaleHelper.kt
│       └── res/                # 布局、图标、字符串等资源
├── wear/                   # 手表端模块
│   └── src/main/
│       ├── java/com/example/yibei/wear/
│       └── res/
└── build.gradle.kts        # 根构建脚本
```

## 快速开始

### 环境要求

- Android Studio Hedgehog（2023.1.1）或更新版本
- JDK 17
- Android SDK（API 26 ~ 34）

### 运行步骤

1. 克隆仓库：
   ```bash
   git clone https://github.com/azj0802/YiBei.git
   ```

2. 用 Android Studio 打开项目，等待 Gradle 同步完成

3. 如需使用 AI 分析功能，在以下 3 个文件中替换你的 DeepSeek API Key：
   - `app/src/main/java/com/example/yibei/ui/HealthFragment.kt`
   - `app/src/main/java/com/example/yibei/ui/ProfileFragment.kt`
   - `app/src/main/java/com/example/yibei/ui/WeeklyReportFragment.kt`

   找到 `YOUR_DEEPSEEK_API_KEY`，替换为：
   ```kotlin
   private const val DEEPSEEK_API_KEY = "sk-你的密钥"
   ```

4. 连接设备或启动模拟器，点击 Run 运行

## 截图

> 待补充

## License

GNU General Public License v3.0

Copyright (c) 2026 邱楷睿

本程序是自由软件：你可以在 GNU GPLv3 许可证的条款下重新发布它和/或修改它。
