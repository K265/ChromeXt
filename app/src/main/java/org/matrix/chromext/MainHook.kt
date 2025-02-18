package org.matrix.chromext

import android.app.AndroidAppHelper
import android.content.Context
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.matrix.chromext.hook.BaseHook
import org.matrix.chromext.hook.ContextMenuHook
import org.matrix.chromext.hook.PageInfoHook
import org.matrix.chromext.hook.PageMenuHook
import org.matrix.chromext.hook.PreferenceHook
import org.matrix.chromext.hook.UserScriptHook
import org.matrix.chromext.hook.WebViewHook
import org.matrix.chromext.utils.Log
import org.matrix.chromext.utils.hookAfter

val supportedPackages =
    arrayOf(
        "com.android.chrome",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "com.chrome.beta",
        "com.chrome.canary",
        "com.chrome.dev",
        "com.kiwibrowser.browser",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.microsoft.emmx.canary",
        "com.microsoft.emmx.dev",
        "com.naver.whale",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.vivaldi.browser",
        "com.vivaldi.browser.snapshot",
        "org.bromite.bromite",
        "org.chromium.thorium",
        "org.cromite.cromite",
        "org.greatfire.freebrowser",
        "org.triple.banana",
        "us.spotco.mulch")

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
  override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
    Log.d(lpparam.processName + " started")
    if (lpparam.packageName == "org.matrix.chromext") {
      return
    }
    if (supportedPackages.contains(lpparam.packageName)) {
      lpparam.classLoader
          .loadClass("org.chromium.ui.base.WindowAndroid")
          .declaredConstructors[1]
          .hookAfter {
            Chrome.init(it.args[0] as Context, lpparam.packageName)
            initHooks(UserScriptHook)
            runCatching {
                  initHooks(PreferenceHook, if (Chrome.isEdge) PageInfoHook else PageMenuHook)
                }
                .onFailure { initHooks(ContextMenuHook) }
          }
    } else {
      val ctx = AndroidAppHelper.currentApplication()
      if (ctx != null && lpparam.packageName != "android") {
        Chrome.init(ctx, ctx.packageName)
      }

      WebViewClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebViewClient::class) {
          WebViewHook.ViewClient = it.thisObject::class.java
          if (WebViewHook.ChromeClient != null) {
            initHooks(WebViewHook, ContextMenuHook)
          }
        }
      }

      WebChromeClient::class.java.declaredConstructors[0].hookAfter {
        if (it.thisObject::class != WebChromeClient::class) {
          WebViewHook.ChromeClient = it.thisObject::class.java
          if (WebViewHook.ViewClient != null) {
            initHooks(WebViewHook, ContextMenuHook)
          }
        }
      }
    }
  }

  override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
    Resource.init(startupParam.modulePath)
  }

  private fun initHooks(vararg hook: BaseHook) {
    hook.forEach {
      if (it.isInit) return@forEach
      it.init()
      it.isInit = true
      Log.d("${it.javaClass.simpleName} hooked")
    }
  }
}
