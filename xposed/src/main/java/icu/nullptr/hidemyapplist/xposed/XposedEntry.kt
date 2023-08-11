package icu.nullptr.hidemyapplist.xposed

import android.content.pm.IPackageManager
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.callbacks.XC_LoadPackage
import icu.nullptr.hidemyapplist.common.Constants
import java.security.cert.X509Certificate
import kotlin.concurrent.thread

private const val TAG = "XposedEntry"

@Suppress("unused")
class XposedEntry : IXposedHookZygoteInit, IXposedHookLoadPackage {

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        EzXHelperInit.initZygote(startupParam)
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        logD(TAG, "package name is ${lpparam.packageName}")
        if (lpparam.packageName == "com.twitter.android") {
            logD(TAG, "-----------------> com.twitter.android ${XposedEntry::class.java.classLoader}")
            EzXHelperInit.initHandleLoadPackage(lpparam)
            findMethod("joj") {
                name == "checkServerTrusted"
            }.hookBefore { param ->
                val cert = param.args[0]
                if (cert is Array<*> && cert.isArrayOf<X509Certificate>()) {
                    cert.forEach { logD(TAG, " cert is $it") }
                }
                logD(TAG, " p2 is ${param.args[1]}")
                param.result = null
            }
        }

        if (lpparam.packageName == Constants.APP_PACKAGE_NAME) {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            hookAllConstructorAfter("icu.nullptr.hidemyapplist.MyApp") {
                getFieldByDesc("Licu/nullptr/hidemyapplist/MyApp;->isHooked:Z").setBoolean(it.thisObject, true)
            }
        } else if (lpparam.packageName == "android") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            logI(TAG, "Hook entry")

            var serviceManagerHook: XC_MethodHook.Unhook? = null
            serviceManagerHook = findMethod("android.os.ServiceManager") {
                name == "addService"
            }.hookBefore { param ->
                if (param.args[0] == "package") {
                    serviceManagerHook?.unhook()
                    val pms = param.args[1] as IPackageManager
                    logD(TAG, "Got pms: $pms")
                    thread {
                        runCatching {
                            UserService.register(pms)
                            logI(TAG, "User service started")
                        }.onFailure {
                            logE(TAG, "System service crashed", it)
                        }
                    }
                }
            }
        }
    }
}
