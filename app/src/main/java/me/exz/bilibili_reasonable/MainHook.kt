package me.exz.bilibili_reasonable

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
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
        Log.i(TAG, "hook Dynamic")
        XposedHelpers.findAndHookMethod(
            hookClass,
            "dynAll",
            "com.bapis.bilibili.app.dynamic.v2.DynAllReq",
            "com.bilibili.lib.moss.api.MossResponseHandler",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.i(TAG, "clean moss response")
                    val mossResponseHandler = param.args[1]
                    val mossResponseHandlerInterface = Class.forName(
                        "com.bilibili.lib.moss.api.MossResponseHandler",
                        true,
                        lpparam.classLoader
                    )
                    Log.i(TAG, mossResponseHandler.javaClass.name)
                    val proxy = Proxy.newProxyInstance(
                        mossResponseHandler.javaClass.classLoader,
                        arrayOf(mossResponseHandlerInterface)
                    ) { _, m, args ->
                        Log.i(TAG, "working on ${m.name}")
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
                                Log.i(TAG, "iter content")
                                try {
                                    Log.i(TAG, "calling getModulesList")
                                    val moduleList =
                                        XposedHelpers.callMethod(e, "getModulesList") as List<*>
                                    Log.i(TAG, "called getModulesList")
                                    for (module in moduleList) {
                                        if (XposedHelpers.callMethod(module, "hasModuleAuthor") as Boolean) {
                                            val moduleAuthor = XposedHelpers.callMethod(module, "getModuleAuthor")
                                            Log.i(TAG, "calling ptime")
                                            val ptime = XposedHelpers.callMethod(moduleAuthor, "getPtimeLabelText")
                                            Log.i(TAG, "called ptime")
                                            Log.i(TAG, ptime.toString())
                                            if (ptime.toString() == "投稿了视频") {
                                                idxList.add(idx)
                                                logLong("removed $moduleAuthor")
                                            }
                                        }
                                    }
                                } catch (e: NoSuchMethodError) {
                                    Log.i(TAG, "wtf no such method")
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

    fun logLong(str: String) {
        if (str.length > 4000) {
            Log.i(TAG, str.substring(0, 4000))
            logLong(str.substring(4000))
        } else Log.i(TAG, str)
    }
}