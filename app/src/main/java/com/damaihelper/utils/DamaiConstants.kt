// ============================================================================
// 📅 创建日期：2026-03-31 10:00
// 🔧 功能：大麦 App 常见控件 ID 和文本常量
//  说明：用于无障碍服务定位和操作 UI 元素
//  版本：v1.0.0
// ============================================================================

package com.damaihelper.utils

/**
 * 大麦 App 控件 ID 常量
 * 
 * 获取方法：
 * 1. 开发者选项 → 显示布局边界
 * 2. 或使用 uiautomatorviewer 工具
 * 3. 或在代码中打印所有节点信息
 */
object DamaiConstants {
    
    // ==================== 搜索相关 ====================
    
    /** 首页搜索框 */
    const val SEARCH_BOX_ID = "cn.damai:id/search_view"
    
    /** 搜索框文本提示 */
    const val SEARCH_HINT_TEXT = "搜索"
    const val SEARCH_HINT_TEXT_ALT = "搜索演出"
    const val SEARCH_HINT_TEXT_ALT2 = "搜索关键字"
    
    /** 搜索按钮 */
    const val SEARCH_BUTTON_TEXT = "搜索"
    const val SEARCH_BUTTON_ID = "cn.damai:id/search_btn"
    
    /** 搜索结果列表 */
    const val SEARCH_RESULT_LIST_ID = "cn.damai:id/recyclerView"
    
    // ==================== 演出详情相关 ====================
    
    /** 立即购买按钮 */
    const val BUY_BUTTON_TEXT = "立即购买"
    const val BUY_BUTTON_TEXT_ALT = "立即预订"
    const val BUY_BUTTON_ID = "cn.damai:id/buyBtn"
    
    /** 选座购票按钮 */
    const val SELECT_SEAT_BUTTON_TEXT = "选座购票"
    
    /** 演出名称 TextView */
    const val CONCERT_NAME_ID = "cn.damai:item_title"
    
    /** 演出日期选择器 */
    const val DATE_SELECTOR_ID = "cn.damai:id/dateView"
    const val DATE_ITEM_CLASS = "cn.damai.module.biz.detail.component.DateView"
    
    // ==================== 票档选择相关 ====================
    
    /** 票档列表容器 */
    const val PRICE_LIST_ID = "cn.damai:id/ticketContainer"
    const val PRICE_LIST_CLASS = "androidx.recyclerview.widget.RecyclerView"
    
    /** 票档项 */
    const val PRICE_ITEM_CLASS = "cn.damai.module.biz.order.TicketItemView"
    const val PRICE_TEXT_CLASS = "android.widget.TextView"
    
    /** 票档状态文本 */
    const val PRICE_STATUS_AVAILABLE = "¥"
    const val PRICE_STATUS_SOLD_OUT = "缺货"
    const val PRICE_STATUS_SOLD_OUT_ALT = "暂时售罄"
    
    /** 确认选座按钮 */
    const val CONFIRM_BUTTON_TEXT = "确认选座"
    const val CONFIRM_BUTTON_ID = "cn.damai:id/confirmBtn"
    
    // ==================== 观众选择相关 ====================
    
    /** 观众列表容器 */
    const val AUDIENCE_LIST_ID = "cn.damai:id/recyclerView"
    const val AUDIENCE_LIST_CLASS = "androidx.recyclerview.widget.RecyclerView"
    
    /** 观众项 */
    const val AUDIENCE_ITEM_CLASS = "cn.damai.module.biz.order.view.ViewerItemView"
    
    /** 观众复选框 */
    const val AUDIENCE_CHECKBOX_ID = "cn.damai:id/cbViewer"
    const val AUDIENCE_NAME_ID = "cn.damai:id/tvViewerName"
    
    /** 添加观众按钮 */
    const val ADD_AUDIENCE_BUTTON_TEXT = "新增观演人"
    
    // ==================== 订单提交相关 ====================
    
    /** 提交订单按钮 */
    const val SUBMIT_ORDER_BUTTON_TEXT = "提交订单"
    const val SUBMIT_ORDER_BUTTON_ID = "cn.damai:id/submitBtn"
    const val SUBMIT_ORDER_BUTTON_ID_ALT = "cn.damai:id/buyBtn"
    
    /** 订单确认页 */
    const val ORDER_CONFIRM_ACTIVITY = "cn.damai.module.biz.order.OrderConfirmActivity"
    
    /** 支付成功页 */
    const val PAYMENT_SUCCESS_ACTIVITY = "cn.damai.module.biz.pay.PaymentSuccessActivity"
    const val PAYMENT_SUCCESS_TEXT = "支付成功"
    
    // ==================== 弹窗相关 ====================
    
    /** 人太多弹窗 */
    const val TOO_MANY_PEOPLE_TEXT = "人太多了"
    const val TOO_MANY_PEOPLE_TEXT_ALT = "前方拥挤"
    const val TOO_MANY_PEOPLE_TEXT_ALT2 = "稍后再试"
    const val TOO_MANY_PEOPLE_BTN_TEXT = "确定"
    
    /** 验证码弹窗 */
    const val CAPTCHA_TEXT = "验证码"
    const val CAPTCHA_INPUT_ID = "cn.damai:id/captchaInput"
    
    /** 缺货提示 */
    const val SOLD_OUT_TEXT = "缺货"
    const val SOLD_OUT_TEXT_ALT = "已售罄"
    
    // ==================== 配送方式相关 ====================
    
    /** 配送方式选择 */
    const val DELIVERY_METHOD_ID = "cn.damai:id/deliveryView"
    const val DELIVERY_ELECTRONIC_TEXT = "电子票"
    const val DELIVERY_MAIL_TEXT = "快递配送"
    const val DELIVERY_PICKUP_TEXT = "现场取票"
    
    // ==================== 通用类名 ====================
    
    /** 通用 TextView */
    const val TEXT_VIEW_CLASS = "android.widget.TextView"
    
    /** 通用 Button */
    const val BUTTON_CLASS = "android.widget.Button"
    
    /** 通用 RecyclerView */
    const val RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView"
    
    /** 通用 EditText */
    const val EDIT_TEXT_CLASS = "android.widget.EditText"
    
    /** 通用 CheckBox */
    const val CHECK_BOX_CLASS = "android.widget.CheckBox"
}
