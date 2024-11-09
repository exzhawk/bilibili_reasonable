package me.exz.bilibili_reasonable

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Proxy

class MainHook : IXposedHookLoadPackage {
    private val TAG = "bilibili_reasonable"
    private val packageName = "tv.danmaku.bili"
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (packageName == lpparam.packageName) {
            hookDynamic(lpparam)
        }
    }

    private fun hookDynamic(lpparam: XC_LoadPackage.LoadPackageParam) {
        val hookClass =
            lpparam.classLoader.loadClass("com.bapis.bilibili.app.dynamic.v2.DynamicMoss") ?: return
        logi("hook Dynamic")
        XposedHelpers.findAndHookMethod(
            hookClass,
            "dynAll",
            "com.bapis.bilibili.app.dynamic.v2.DynAllReq",
            "com.bilibili.lib.moss.api.MossResponseHandler",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    logi("clean moss response")
                    val mossResponseHandler = param.args[1]
                    val mossResponseHandlerInterface = Class.forName(
                        "com.bilibili.lib.moss.api.MossResponseHandler",
                        true,
                        lpparam.classLoader
                    )
                    logi(mossResponseHandler.javaClass.name)
                    val proxy = Proxy.newProxyInstance(
                        mossResponseHandler.javaClass.classLoader,
                        arrayOf(mossResponseHandlerInterface)
                    ) { _, m, args ->
                        logi("working on ${m.name}")
                        if (m.name == "onNext") {
                            val reply = args[0]
                            val dynamicList = XposedHelpers.callMethod(reply, "getDynamicList")
                            XposedHelpers.callMethod(dynamicList, "ensureListIsMutable")
                            val contentList =
                                XposedHelpers.callMethod(
                                    dynamicList,
                                    "getListList"
                                ) as MutableList<*>
                            val idxList = mutableSetOf<Int>()
                            for ((idx, e) in contentList.withIndex()) {
                                logi("iter content")
                                try {
                                    logi("calling getModulesList")
                                    val moduleList =
                                        XposedHelpers.callMethod(e, "getModulesList") as List<*>
                                    logi("called getModulesList")
                                    for (module in moduleList) {
                                        if (XposedHelpers.callMethod(module, "hasModuleAuthor") as Boolean) {
                                            val moduleAuthor = XposedHelpers.callMethod(module, "getModuleAuthor")
                                            logi("calling ptime")
                                            val ptime = XposedHelpers.callMethod(moduleAuthor, "getPtimeLabelText")
                                            logi("called ptime")
                                            logi(ptime.toString())
                                            if (ptime.toString() == "投稿了视频") {
                                                idxList.add(idx)
                                                logLong("removed $moduleAuthor")
                                            }
                                        }
                                    }
                                } catch (e: NoSuchMethodError) {
                                    logi("wtf no such method")
                                    continue
                                }
                            }
                            idxList.reversed().forEach {
                                contentList.removeAt(it)
                            }
                            m.invoke(mossResponseHandler, *args)
                        } else if (args == null) {
                            m.invoke(mossResponseHandler)
                        } else {
                            m.invoke(mossResponseHandler, *args)
                        }
                    }
                    param.args[1] = proxy
                }
            }
        )
    }

    fun logi(str: String) {
        XposedBridge.log("[$TAG] $str")
    }

    fun logLong(str: String) {
        if (str.length > 4000) {
            Log.i(TAG, str.substring(0, 4000))
            logLong(str.substring(4000))
        } else Log.i(TAG, str)
    }
}