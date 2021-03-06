/**
 * Created by amoryin on 2020/2/8.
 */

package com.yzproj.echarts.client

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Message
import android.util.AttributeSet
import android.view.View
import android.webkit.*
import android.widget.Toast
import com.github.abel533.echarts.json.GsonOption
import com.yzproj.echarts.face.EChartsDataSource
import com.yzproj.echarts.face.EChartsEventAction
import com.yzproj.echarts.face.EChartsEventHandler
import com.yzproj.echarts.face.EChartsWebClient
import kotlin.concurrent.thread

class EChartWebView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0)
    : WebView(context, attrs, defStyleAttr) {
    init {
        init();
    }

    @SuppressLint("JavascriptInterface")
    private fun init(){
        setLayerType(View.LAYER_TYPE_SOFTWARE,null)

        val webSettings = settings;
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.setSupportZoom(true)
        webSettings.displayZoomControls = true

        addJavascriptInterface(EChartInterface(context),"Android")
    }

    fun setType(type: Int) {
        var index = type
        if(type < 0 || type > 2) {
            loadUrl("file:///android_asset/echart/biz/echart.html")
        }else{
            loadUrl("file:///android_asset/echart/biz/echart-$index.html")
        }
    }
    /**
     *
     */
    private var chartLoaded:Boolean = false

    fun isChartLoaded():Boolean{
        return chartLoaded
    }

    private val mWebView:WebView = this;
    /**
     * dataSource
     */
    private var dataSource: EChartsDataSource? = null

    fun setDataSource(data:EChartsDataSource){
        dataSource = data
        reload()
    }

    /**
     * delegate
     */
    private var delegate: EChartsEventHandler? = null

    fun setDelegate(data: EChartsEventHandler){
        delegate = data
    }

    private val mHandler:Handler = Handler(Handler.Callback { msg ->

        if (msg.what == 1){
            val action = msg.obj.toString()
            delegate?.onHandlerResponseRemoveAction(mWebView,EChartsEventAction.convert(action))
        }else if (msg.what == 2){
            val obj = msg.obj as Array<String?>
            val action = obj.get(0);val data = obj.get(1)
            delegate?.onHandlerResponseAction(mWebView,EChartsEventAction.convert(action!!),data)
        }

        false
    })

    /**
     * refresh
     */
    fun refreshEchartsWithOption(option: GsonOption?){
        val optionString = option.toString()
        val call = "javascript:refreshEchartsWithOption('$optionString')"
        loadUrl(call)
    }

    /**
     * interface
     */
    internal inner class EChartInterface(var context: Context){
        val chartOptions: String?
            @JavascriptInterface
            get() {
                if (dataSource!=null){
                    val option = dataSource!!.echartOptions(mWebView)
                    if (option == null) return  dataSource!!.echartOptionsString(mWebView)
                    return option.toString()
                }
                return null
            }

        @JavascriptInterface
        fun showMessage(msg:String?):Boolean{
            if (msg == null || msg.isEmpty()) return false
            Toast.makeText(context,msg,Toast.LENGTH_SHORT).show()
            return true
        }

        @JavascriptInterface
        fun chartViewLoading(){
            chartLoaded = false;
        }

        @JavascriptInterface
        fun chartViewLoaded(){
            chartLoaded = true;
        }

        @JavascriptInterface
        fun removeEChartActionEventResponse(action: String){
            val msg = Message();msg.what = 1;msg.obj = action
            mHandler.sendMessageDelayed(msg,1)
        }

        @JavascriptInterface
        fun onEChartActionEventResponse(action: String,data:String?){
            val msg = Message();msg.what = 2;msg.obj = arrayOf(action,data)
            mHandler.sendMessageDelayed(msg,1)
        }
    }

    /**
     * webClient
     */

    private var client: EChartsWebClient? = null

    fun setClient(data:EChartsWebClient?){
        client = data
    }

    /**
     * reset
     */
    fun reloadActions(){
        if (dataSource!=null){
            var removeOptions = dataSource!!.removeEChartActionEvents(mWebView)
            if (!removeOptions.isNullOrEmpty()){
                for (e:EChartsEventAction in removeOptions.iterator()){
                    val action = e.ation
                    val call = "javascript:removeEchartActionHandler('" + action + "')"
                    loadUrl(call)
                }
            }

            var addOptions = dataSource!!.addEChartActionEvents(mWebView)
            if (!addOptions.isNullOrEmpty()){
                for (e:EChartsEventAction in addOptions.iterator()){
                    val action = e.ation
                    val call = "javascript:addEchartActionHandler('" + action + "')"
                    loadUrl(call)
                }
            }
        }
    }

    fun showLoading(){
        val call = "javascript:showChartLoading()"
        loadUrl(call)
    }

    fun hideLoading(){
        val call = "javascript:hideChartLoading()"
        loadUrl(call)
    }

    override fun loadUrl(url: String?) {
        super.loadUrl(url)
        // 设置WebViewClient
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(url)
                client?.shouldOverrideUrlLoading(mWebView,view,request)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                client?.onPageStarted(mWebView,view,url,favicon)
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                client?.onPageFinished(mWebView,view,url)
                if(chartLoaded)reloadActions()
                super.onPageFinished(view, url)
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                client?.onLoadResource(mWebView,view,url)
                super.onLoadResource(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
    //				view.loadData(errorHtml, "text/html; charset=UTF-8", null);
                hideLoading()
                client?.onReceivedError(mWebView,view,request,error)
                super.onReceivedError(view, request, error)
            }
        }

        // 设置WebChromeClient
        webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                client?.onJsAlert(mWebView,view,url,message,result)
                return super.onJsAlert(view, url, message, result)
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                client?.onJsConfirm(mWebView,view,url,message,result)
                return super.onJsConfirm(view, url, message, result)
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                client?.onJsPrompt(mWebView,view,url,message,defaultValue,result)
                return super.onJsPrompt(view, url, message, defaultValue, result)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                client?.onProgressChanged(mWebView,view,newProgress)
                super.onProgressChanged(view, newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                client?.onReceivedTitle(mWebView,view,title)
                super.onReceivedTitle(view, title)
            }
        }
    }
}