package com.example.hyperisland.xposed

import android.app.Notification
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Xposed Hook类
 * 用于Hook小米下载管理器并显示灵动岛通知
 */
class DownloadHook : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "HyperIsland"
        private const val TARGET_PACKAGE = "com.android.providers.downloads"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // 只hook下载管理器
        if (lpparam.packageName != TARGET_PACKAGE) return

        Log.d(TAG, "Hooking $TARGET_PACKAGE")
        XposedBridge.log("HyperIsland: Hooking $TARGET_PACKAGE")

        try {
            val nmClass = lpparam.classLoader.loadClass("android.app.NotificationManager")

            // Hook notify(int id, Notification n)
            XposedHelpers.findAndHookMethod(
                nmClass,
                "notify",
                Int::class.javaPrimitiveType,
                Notification::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val id = param.args[0] as? Int ?: return
                            val notif = param.args[1] as? Notification ?: return

                            XposedBridge.log("HyperIsland: Intercepting notification #$id")

                            IslandInjector.inject(id, notif, lpparam)
                        } catch (e: Throwable) {
                            Log.e(TAG, "Error in notify hook", e)
                            XposedBridge.log("HyperIsland: Hook error - ${e.message}")
                        }
                    }
                }
            )

            XposedBridge.log("HyperIsland: Hooked NotificationManager.notify")

        } catch (e: Throwable) {
            Log.e(TAG, "Error hooking download manager", e)
            XposedBridge.log("HyperIsland: Error - ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 灵动岛注入器
     */
    object IslandInjector {

        fun inject(id: Int, notif: Notification, lpparam: XC_LoadPackage.LoadPackageParam) {
            try {
                val extras = notif.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""

                // 检查是否是下载通知
                if (!isDownloadNotification(title, extras)) {
                    return
                }

                XposedBridge.log("HyperIsland: Download notification detected: $title")

                // 获取进度信息
                val progress = extras.getInt("progress", 0)
                val contentText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

                XposedBridge.log("HyperIsland: Progress - $title: $progress%")

                // 获取application context
                val context = getContext(lpparam) ?: run {
                    Log.e(TAG, "Failed to get context")
                    return
                }

                // 构建灵动岛参数
                val islandParams = buildIslandParams(title, contentText, progress)

                // 创建图标
                val picsBundle = Bundle()
                val iconId = context.resources.getIdentifier("stat_sys_download", "drawable", "android")
                if (iconId != 0) {
                    val icon = Icon.createWithResource(context, iconId)
                    picsBundle.putParcelable("miui.focus.pic_ticker", icon)
                }

                // 注入灵动岛参数到notification extras
                extras.putBundle("miui.focus.pics", picsBundle)
                extras.putString("miui.focus.param", islandParams)
                extras.putInt("miui.focus.type", 2)

                XposedBridge.log("HyperIsland: Island params injected successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Error injecting island params", e)
                XposedBridge.log("HyperIsland: Inject error - ${e.message}")
            }
        }

        private fun isDownloadNotification(title: String, extras: Bundle): Boolean {
            // 检查标题
            if (title.contains("下载") ||
                title.contains("Downloading") ||
                title.contains("download") ||
                title.contains("DOWNLOAD")) {
                return true
            }

            // 检查是否有进度字段
            if (extras.containsKey("progress")) {
                return true
            }

            return false
        }

        private fun getContext(lpparam: XC_LoadPackage.LoadPackageParam): android.content.Context? {
            return try {
                val activityThread = lpparam.classLoader.loadClass("android.app.ActivityThread")
                activityThread.getMethod("currentApplication").invoke(null) as? android.content.Context
            } catch (e: Exception) {
                Log.e(TAG, "Error getting context", e)
                null
            }
        }

        private fun buildIslandParams(title: String, content: String, progress: Int): String {
            val paramV2 = org.json.JSONObject().apply {
                put("protocol", 1)
                put("business", "download")
                put("enableFloat", true)
                put("updatable", progress >= 0 && progress < 100)

                // 状态栏数据
                put("ticker", title)
                put("tickerPic", "miui.focus.pic_ticker")

                // 息屏AOD数据
                put("aodTitle", if (progress > 0) "下载中 $progress%" else title)
                put("aodPic", "miui.focus.pic_ticker")

                // 岛数据
                val paramIsland = org.json.JSONObject().apply {
                    put("islandProperty", 1)

                    // 大岛内容
                    val bigIslandArea = org.json.JSONObject().apply {
                        val imageTextInfoLeft = org.json.JSONObject().apply {
                            put("type", 1)

                            val picInfo = org.json.JSONObject().apply {
                                put("type", 1)
                                put("pic", "miui.focus.pic_ticker")
                            }
                            put("picInfo", picInfo)

                            val textInfo = org.json.JSONObject().apply {
                                put("frontTitle", if (progress >= 100) "完成" else "下载中")
                                put("title", if (progress >= 0) "$progress%" else content)
                                put("content", "正在下载")
                                put("useHighLight", progress >= 100)
                            }
                            put("textInfo", textInfo)
                        }
                        put("imageTextInfoLeft", imageTextInfoLeft)
                    }
                    put("bigIslandArea", bigIslandArea)

                    // 小岛内容
                    val smallIslandArea = org.json.JSONObject().apply {
                        val picInfo = org.json.JSONObject().apply {
                            put("type", 1)
                            put("pic", "miui.focus.pic_ticker")
                        }
                        put("picInfo", picInfo)

                        val textInfo = org.json.JSONObject().apply {
                            put("title", if (progress >= 0) "$progress%" else "下载")
                        }
                        put("textInfo", textInfo)
                    }
                    put("smallIslandArea", smallIslandArea)
                }
                put("param_island", paramIsland)

                // 焦点通知数据
                val baseInfo = org.json.JSONObject().apply {
                    put("title", title)
                    put("content", content)
                    put("colorTitle", "#006EFF")
                    put("type", 2)
                }
                put("baseInfo", baseInfo)

                // 提示信息
                val hintInfo = org.json.JSONObject().apply {
                    put("type", 1)
                    put("title", if (progress >= 100) "下载完成" else "下载中")
                }
                put("hintInfo", hintInfo)
            }

            val root = org.json.JSONObject().apply {
                put("param_v2", paramV2)
            }

            return root.toString()
        }
    }
}
