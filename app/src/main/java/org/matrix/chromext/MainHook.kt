package org.matrix.chromext

import android.content.Context
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.GestureNavHook
import org.matrix.chromext.hook.IntentHook
import org.matrix.chromext.hook.MenuHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.findMethod
import org.matrix.chromext.utils.hookAfter

private const val PACKAGE_CHROME = "com.android.chrome"
private const val PACKAGE_BETA = "com.chrome.beta"
private const val PACKAGE_DEV = "com.chrome.dev"
// private const val PACKAGE_CANARY = "com.chrome.canary"

fun filterPackage(packageName: String): Boolean {
  return packageName == PACKAGE_BETA ||
      packageName == PACKAGE_DEV ||
      // packageName == PACKAGE_CANARY ||
      packageName == PACKAGE_CHROME
}

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit, IXposedHookInitPackageResources {

  var split = true

  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    if (filterPackage(lpparam.packageName)) {
      runCatching {
            findMethod(
                    lpparam.classLoader.loadClass(
                        "org.chromium.chrome.browser.base.SplitChromeApplication")) {
                      name == "attachBaseContext"
                    }
                .hookAfter {
                  val ctx = (it.args[0] as Context).createContextForSplit("chrome")
                  initHooks(ctx, lpparam.packageName)
                }
          }
          .onFailure {
            split = false
            findMethod(
                    lpparam.classLoader.loadClass(
                        "org.chromium.chrome.browser.ChromeTabbedActivity"),
                    true) {
                      name == "onCreate"
                    }
                .hookAfter { initHooks(it.thisObject as Context, lpparam.packageName) }
          }
    }
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    ResourceMerge.init(startupParam.modulePath)
  }

  override fun handleInitPackageResources(
      resparam: XC_InitPackageResources.InitPackageResourcesParam
  ) {}

  private fun initHooks(ctx: Context, packageName: String) {
    arrayOf(UserScriptHook, GestureNavHook, MenuHook, IntentHook).forEach {
      runCatching {
            if (it.isInit) return@forEach
            it.init(ctx, split)
            it.isInit = true
            Log.i("Inited hook for ${packageName}: ${it.javaClass.simpleName}")
          }
          .onFailure { Log.ex(it) }
    }
  }
}
