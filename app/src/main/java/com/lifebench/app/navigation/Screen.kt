package com.lifebench.app.navigation

/**
 * 全局路由常量。所有页面跳转都引用这里，避免魔法字符串散落。
 * 底部五大主导航 + 各子页面；带参路由用路由模板字符串（参数以 {name} 占位）。
 */
object Routes {
    // —— 五大主导航（底部导航栏）——
    const val HOME = "home"
    const val TOOLS = "tools"
    const val BRAIN = "brain"
    const val FIT = "fit"
    const val PROFILE = "profile"

    // —— 生活工具子页 ——
    const val TODO = "todo"
    const val PASSWORD = "password"
    const val NOTE = "note"
    const val ANNIVERSARY = "anniversary"
    const val SETTINGS = "settings"

    // —— 健身饮食子页 ——
    const val FOCUS = "focus"
    const val SLEEP = "sleep"
    const val ACCOUNT = "account"
    const val DIET = "diet"
    const val FITNESS = "fitness"

    // —— 脑力训练 ——
    const val SCHULTE = "schulte"                 // 舒尔特方格
    const val BRAIN_TRAIN = "brain_train/{category}" // 通用脑力训练，带分类参数（专注力/记忆力/逻辑）
}
